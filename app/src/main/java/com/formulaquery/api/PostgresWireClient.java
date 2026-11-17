package com.formulaquery.api;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * A from-scratch implementation of the PostgreSQL frontend/backend wire
 * protocol (v3), built directly on a raw TCP socket — no JDBC driver,
 * no third-party library.
 *
 * Supports:
 *   - Startup handshake
 *   - trust / cleartext / md5 password authentication
 *   - The "simple query" protocol (single SQL statement, text results)
 *
 * Deliberately NOT supported (documented limitation, not a bug):
 *   - SCRAM-SHA-256 authentication (Postgres's modern default) — this
 *     requires a full PBKDF2/HMAC-SHA256 implementation, which is out
 *     of scope here. Configure the target Postgres user for md5 or
 *     trust auth to use this client.
 *   - SSL/TLS — this client speaks plain, unencrypted Postgres wire
 *     protocol only. Use a local/non-SSL Postgres instance.
 *
 * Protocol reference: each message is [1-byte type][4-byte length
 * including itself][payload]. The very first message the client sends
 * (StartupMessage) has NO type byte — it's the only exception.
 */
public class PostgresWireClient implements Closeable {

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

    private PostgresWireClient(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    public static PostgresWireClient connect(String host, int port, String database,
                                             String user, String password) throws IOException {
        Socket socket = new Socket(host, port);
        socket.setSoTimeout(15000);
        PostgresWireClient client = new PostgresWireClient(socket);
        client.sendStartupMessage(database, user);
        client.authenticate(user, password);
        client.drainUntilReadyForQuery();
        return client;
    }

    // =========================================================================
    // STARTUP
    // =========================================================================

    private void sendStartupMessage(String database, String user) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream bodyOut = new DataOutputStream(body);

        bodyOut.writeShort(3); // protocol major version 3
        bodyOut.writeShort(0); // minor version 0
        writeCString(bodyOut, "user");
        writeCString(bodyOut, user);
        writeCString(bodyOut, "database");
        writeCString(bodyOut, database);
        writeCString(bodyOut, "client_encoding");
        writeCString(bodyOut, "UTF8");
        bodyOut.writeByte(0); // terminator (empty string = end of parameter list)

        byte[] bodyBytes = body.toByteArray();
        out.writeInt(bodyBytes.length + 4); // length field includes itself
        out.write(bodyBytes);
        out.flush();
    }

    // =========================================================================
    // AUTHENTICATION
    // =========================================================================

    private void authenticate(String user, String password) throws IOException {
        while (true) {
            int msgType = in.readUnsignedByte();
            int length = in.readInt();
            int remaining = length - 4;

            if (msgType == 'R') { // Authentication*
                int authType = in.readInt();
                remaining -= 4;

                switch (authType) {
                    case 0: // AuthenticationOk
                        skip(remaining);
                        return;

                    case 3: // AuthenticationCleartextPassword
                        skip(remaining);
                        sendPasswordMessage(password);
                        break;

                    case 5: { // AuthenticationMD5Password
                        byte[] salt = new byte[4];
                        in.readFully(salt);
                        remaining -= 4;
                        skip(remaining);
                        sendPasswordMessage(md5Hash(user, password, salt));
                        break;
                    }

                    default:
                        skip(remaining);
                        throw new IOException(
                                "Unsupported auth method (code " + authType + "). This client only supports " +
                                        "trust, cleartext, and md5 password auth — SCRAM-SHA-256 is not implemented. " +
                                        "Configure the Postgres user for md5 auth (pg_hba.conf) to connect."
                        );
                }

            } else if (msgType == 'E') {
                throw new IOException("Postgres error: " + readErrorMessage(remaining));
            } else {
                skip(remaining);
            }
        }
    }

    private String md5Hash(String user, String password, byte[] salt) throws IOException {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");

            md5.update(password.getBytes(StandardCharsets.UTF_8));
            md5.update(user.getBytes(StandardCharsets.UTF_8));
            String step1Hex = toHex(md5.digest());

            md5.reset();
            md5.update(step1Hex.getBytes(StandardCharsets.UTF_8));
            md5.update(salt);
            return "md5" + toHex(md5.digest());

        } catch (Exception e) {
            throw new IOException("MD5 hashing failed", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void sendPasswordMessage(String password) throws IOException {
        byte[] pwBytes = password.getBytes(StandardCharsets.UTF_8);
        out.writeByte('p');
        out.writeInt(pwBytes.length + 4 + 1); // length(4) + string + null terminator
        out.write(pwBytes);
        out.writeByte(0);
        out.flush();
    }

    private void drainUntilReadyForQuery() throws IOException {
        while (true) {
            int msgType = in.readUnsignedByte();
            int length = in.readInt();
            int remaining = length - 4;

            if (msgType == 'Z') { // ReadyForQuery
                skip(remaining);
                return;
            } else if (msgType == 'E') {
                throw new IOException("Postgres error: " + readErrorMessage(remaining));
            } else {
                skip(remaining); // ParameterStatus, BackendKeyData, etc — not needed here
            }
        }
    }

    // =========================================================================
    // SIMPLE QUERY PROTOCOL
    // =========================================================================

    public List<Map<String, Object>> executeQuery(String sql) throws IOException {

        out.writeByte('Q');
        byte[] sqlBytes = sql.getBytes(StandardCharsets.UTF_8);
        out.writeInt(sqlBytes.length + 4 + 1);
        out.write(sqlBytes);
        out.writeByte(0);
        out.flush();

        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();

        while (true) {
            int msgType = in.readUnsignedByte();
            int length = in.readInt();
            int remaining = length - 4;

            switch (msgType) {

                case 'T': { // RowDescription
                    int fieldCount = in.readShort();
                    remaining -= 2;
                    columns.clear();
                    for (int i = 0; i < fieldCount; i++) {
                        String colName = readCString();
                        remaining -= (colName.length() + 1);
                        // tableOid(4) + colAttrNum(2) + typeOid(4) + typeLen(2) + typeMod(4) + formatCode(2) = 18 bytes
                        skip(18);
                        remaining -= 18;
                        columns.add(colName);
                    }
                    if (remaining > 0) skip(remaining);
                    break;
                }

                case 'D': { // DataRow
                    int fieldCount = in.readShort();
                    remaining -= 2;
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 0; i < fieldCount; i++) {
                        int valLen = in.readInt();
                        remaining -= 4;
                        String colName = i < columns.size() ? columns.get(i) : ("col" + i);
                        if (valLen == -1) {
                            row.put(colName, null);
                        } else {
                            byte[] valBytes = new byte[valLen];
                            in.readFully(valBytes);
                            remaining -= valLen;
                            row.put(colName, new String(valBytes, StandardCharsets.UTF_8));
                        }
                    }
                    if (remaining > 0) skip(remaining);
                    rows.add(row);
                    break;
                }

                case 'C': // CommandComplete
                    skip(remaining);
                    break;

                case 'Z': // ReadyForQuery — query is done
                    skip(remaining);
                    return rows;

                case 'E':
                    throw new IOException("Postgres error: " + readErrorMessage(remaining));

                default:
                    skip(remaining); // NoticeResponse, EmptyQueryResponse, etc
            }
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private void writeCString(DataOutputStream o, String s) throws IOException {
        o.write(s.getBytes(StandardCharsets.UTF_8));
        o.writeByte(0);
    }

    private String readCString() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.readUnsignedByte()) != 0) {
            buf.write(b);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    private String readErrorMessage(int remaining) throws IOException {
        byte[] data = new byte[remaining];
        in.readFully(data);
        // Error fields are a sequence of [1-byte field code][null-terminated string],
        // terminated by a zero byte. We don't need full structure — just surface
        // something readable for the response message.
        return new String(data, StandardCharsets.UTF_8).replace('\u0000', ' ').trim();
    }

    private void skip(int n) throws IOException {
        while (n > 0) {
            long skipped = in.skip(n);
            if (skipped <= 0) {
                if (in.read() == -1) throw new EOFException("Unexpected end of stream from Postgres server");
                skipped = 1;
            }
            n -= skipped;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            out.writeByte('X'); // Terminate message
            out.writeInt(4);
            out.flush();
        } catch (IOException ignored) {
            // best-effort — the socket close below is what actually matters
        }
        socket.close();
    }
}
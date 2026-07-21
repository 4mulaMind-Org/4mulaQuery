import java.io.*;

public class DatabaseBridge {
    // Ye variable aapke C++ executable ka path rakhega
    private static final String ENGINE_PATH = "../4mulaQuery";

    public static void main(String[] args) {
        System.out.println("--- 4mulaQuery Java Bridge Started ---");
        
        // Example: Java se data insert karna
        String result = executeCommand("insert 4 JavaUser java@test.com");
        System.out.println("Engine Response: " + result);

        // Example: Java se saara data mangwana
        String allData = executeCommand("select");
        System.out.println("\n--- Current Data in Database ---");
        System.out.println(allData);
    }

    // Ye function asli magic hai jo C++ se baat karta hai
    public static String executeCommand(String cmd) {
        StringBuilder output = new StringBuilder();
        try {
            // 1. ProcessBuilder se C++ engine ko background mein start karo
            ProcessBuilder pb = new ProcessBuilder(ENGINE_PATH);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 2. C++ Engine ko command bhejo (Jaise terminal mein type karte ho)
            OutputStream os = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
            writer.write(cmd + "\nexit\n"); // Command ke baad 'exit' zaroori hai flush ke liye
            writer.flush();
            writer.close();

            // 3. C++ Engine ka result wapas read karo
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            // DatabaseBridge.java ke andar loop ko aise badlo:

        while ((line = reader.readLine()) != null) {
            // Sirf wahi lines save karo jo 'ID:', 'Success:', ya 'Result:' se shuru ho rahi hon
            if (line.startsWith("ID:") || line.startsWith("Success:") || line.startsWith("Result:")) {
                output.append(line).append("\n");
            }
        }
            process.waitFor();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
        return output.toString();
    }
}
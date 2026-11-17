path = "app/src/main/resources/static/app.js"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

if "runDatasetNLPQuery" in content:
    print("Already present, skipping")
else:
    addition = """

async function runDatasetNLPQuery() {

  const datasetName = document.getElementById("datasetSelect").value;
  const query = document.getElementById("datasetNlpCmd").value.trim();

  if (!datasetName) {
    document.getElementById("datasetViewer").innerHTML =
      '<div class="log"><span class="log-err">Pehle ek dataset select karo dropdown se</span></div>';
    return;
  }

  if (!query) return;

  try {

    const res = await fetch("/api/dataset/" + datasetName + "/nlp-query", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ query: query })
    });

    const data = await res.json();

    if (data.supported === false) {
      document.getElementById("datasetViewer").innerHTML =
        '<div class="log"><span class="log-err">Not supported</span><br><span class="log-time">' + data.message + '</span></div>';
      return;
    }

    if (!data.success) {
      document.getElementById("datasetViewer").innerHTML =
        '<div class="log"><span class="log-err">' + data.message + '</span></div>';
      return;
    }

    const condText = (data.conditions || [])
      .map(function(c) { return c.field + " " + c.op + " " + c.value; })
      .join(" AND ");

    const genLine = condText
      ? '<p style="color:#888;font-size:0.8rem;margin-bottom:8px;">Filter: <span style="color:#d4af37">' + condText + '</span> - ' + data.total + ' rows</p>'
      : '<p style="color:#888;font-size:0.8rem;margin-bottom:8px;">Showing all rows - ' + data.total + ' rows</p>';

    if (!data.records || data.records.length === 0) {
      document.getElementById("datasetViewer").innerHTML =
        genLine + '<div class="empty"><div class="empty-icon">-</div>No matching records found.</div>';
      return;
    }

    const headers = Object.keys(data.records[0]);

    let html = genLine + '<div style="overflow-x:auto;"><table style="width:100%;border-collapse:collapse;font-size:0.82rem;">';

    html += '<tr>' + headers.map(function(h) {
      return '<th style="border:1px solid #2a3a5c;padding:8px;background:#1b2a4a;color:#d4af37;">' + h + '</th>';
    }).join('') + '</tr>';

    data.records.slice(0, 50).forEach(function(row) {
      html += '<tr>' + headers.map(function(h) {
        return '<td style="border:1px solid #2a3a5c;padding:8px;color:#c8d8f0;">' + (row[h] || '') + '</td>';
      }).join('') + '</tr>';
    });

    html += '</table></div>';

    document.getElementById("datasetViewer").innerHTML = html;

  } catch (e) {
    document.getElementById("datasetViewer").innerHTML =
      '<div class="log"><span class="log-err">Error: ' + e.message + '</span></div>';
  }
}
"""
    with open(path, "a", encoding="utf-8") as f:
        f.write(addition)
    print("SUCCESSFULLY APPENDED")

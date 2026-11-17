path = "app/src/main/resources/static/index.html"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

old = '<div id="datasetViewer"></div>'

new = """<div style="font-family:'DM Mono',monospace;font-size:0.72rem;color:var(--text2);margin:14px 0 8px;">Ask about this dataset</div>
<div class="console-input" style="margin-bottom:16px;">
  <span>Q</span>
  <input type="text" id="datasetNlpCmd" placeholder="e.g. employees older than 30 in Delhi" onkeydown="if(event.key==='Enter')runDatasetNLPQuery()"/>
  <button class="btn btn-exec" onclick="runDatasetNLPQuery()" style="padding:8px 18px;font-size:0.82rem">Ask</button>
</div>
<div id="datasetViewer"></div>"""

count = content.count(old)

if count == 0:
    print("MATCH NAHI MILA")
else:
    content = content.replace(old, new, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("SUCCESSFULLY UPDATED")

/**
 * =====================================================
 *    4mulaQuery — app.js
 *    Author: Abdul Qadir
 *    Sections:
 *    1. Typing Animation
 *    2. Auth (Login / Signup / Forgot / Logout)
 *    3. App Load & Navigation
 *    4. API (Insert / Search / Delete / FetchAll)
 * =====================================================
 *
 * @format
 */

/* ── 1. TYPING ANIMATION ── */
const phrases = [
  "Fastest C++ B-Tree Engine",
  "Spring Boot + Docker Integrated",
  "Intelligent Query Processing",
  "AI Powered SQL Optimization",
  "Real-Time Query Performance Insights",
  "Machine Learning Driven Database Engine",
  "Next Generation Query Analyzer",
  "Smart Database Exploration",
  "Predictive SQL Execution Engine",
  "Developed by Abdul Qadir",
];
let pIndex = 0,
  cIndex = 0,
  deleting = false,
  typingTimer = null;

function startTyping() {
  if (typingTimer) clearTimeout(typingTimer);
  pIndex = 0;
  cIndex = 0;
  deleting = false;
  typeNext();
}
function typeNext() {
  const el = document.getElementById("dynamic-text");
  if (!el) return;
  const text = phrases[pIndex];
  el.textContent = deleting
    ? text.substring(0, cIndex--)
    : text.substring(0, cIndex++);
  if (!deleting && cIndex > text.length) {
    deleting = true;
    typingTimer = setTimeout(typeNext, 2000);
  } else if (deleting && cIndex === 0) {
    deleting = false;
    pIndex = (pIndex + 1) % phrases.length;
    typingTimer = setTimeout(typeNext, 500);
  } else {
    typingTimer = setTimeout(typeNext, deleting ? 50 : 100);
  }
}

/* ───────────────── AUTH SYSTEM ─────────────────
   Ye section frontend authentication logic handle karta hai.

   Responsibilities:
   • Current logged-in user track karna
   • OTP reset flow manage karna
   • LocalStorage me users aur session save karna
   • Toast notifications show karna
   • Login / Signup / Forgot password pages switch karna
*/

// Current logged-in user object
let currentUser = null,

  // OTP verification ke liye generated OTP
  generatedOtp = null,

  // OTP kis email par bheja gaya hai
  otpEmail = null;



// ───────────── LOCAL STORAGE HELPERS ─────────────

/*
getUsers()

Purpose:
LocalStorage se saare registered users load karna.

Storage key:
fq_users

Return:
JSON object jisme saare users stored hote hain
*/
function getUsers() {
  return JSON.parse(localStorage.getItem("fq_users") || "{}");
}


/*
saveUsers()

Purpose:
Users object ko LocalStorage me save karna.

Parameter:
u → users object
*/
function saveUsers(u) {
  localStorage.setItem("fq_users", JSON.stringify(u));
}


/*
getSession()

Purpose:
Current logged-in user session load karna.

Storage key:
fq_session

Return:
User object ya null
*/
function getSession() {
  return JSON.parse(localStorage.getItem("fq_session") || "null");
}


/*
saveSession()

Purpose:
Current user session ko LocalStorage me store karna
taaki page reload hone par bhi login state maintain rahe.

Parameter:
u → user object
*/
function saveSession(u) {
  localStorage.setItem("fq_session", JSON.stringify(u));
}



// ───────────── TOAST NOTIFICATION ─────────────

/*
showToast()

Purpose:
User ko temporary notification message show karna.

Parameters:
id   → toast element id
msg  → message text
type → error / success

Toast 3.5 seconds baad automatically hide ho jata hai.
*/
function showToast(id, msg, type = "error") {

  // Toast element fetch karo
  const el = document.getElementById(id);

  // Message set karo
  el.textContent = msg;

  // CSS class apply karo (error / success)
  el.className = `toast ${type} show`;

  // 3.5 seconds baad hide karo
  setTimeout(() => (el.className = `toast ${type}`), 3500);
}



// ───────────── PAGE SWITCHING ─────────────

/*
showPage()

Purpose:
Login / Signup / Forgot password pages ke beech switch karna.

Parameter:
id → jis page ko show karna hai
*/
function showPage(id) {

  // Saare auth pages hide karo
  ["loginPage", "signupPage", "forgotPage"].forEach((p) => {
    document.getElementById(p).style.display = "none";
  });

  // Selected page show karo
  document.getElementById(id).style.display = "flex";

  /*
  Agar user "Forgot Password" page par aaye to
  uske multi-step flow ko reset karo
  */

  if (id === "forgotPage") {

    // Step 1 → Email enter
    document.getElementById("fStep1").style.display = "block";

    // Step 2 → OTP verify
    document.getElementById("fStep2").style.display = "none";

    // Step 3 → New password set
    document.getElementById("fStep3").style.display = "none";
  }
}

// ───────────────── LOGIN FUNCTION ─────────────────

/*
doLogin()

Purpose:
User ko backend API ke through authenticate karna.

Flow:
1. Login form se email aur password read karo
2. Basic validation check karo
3. Backend endpoint (/api/auth/login) ko POST request bhejo
4. Response check karo
5. Agar login successful ho:
      - user session save karo
      - main app load karo
6. Agar error aaye to toast message show karo
*/

async function doLogin() {

  // Form se email aur password lo
  const email = document.getElementById('loginEmail').value.trim();
  const pass  = document.getElementById('loginPass').value;

  // Empty fields validation
  if (!email || !pass)
    return showToast('loginToast', 'Please fill all fields');

  try {

    // Backend login API call
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },

      // Request body JSON format me
      body: JSON.stringify({
        email,
        password: pass
      })
    });

    // Response JSON me convert karo
    const data = await res.json();

    // Agar login fail hua
    if (!data.success)
      return showToast('loginToast', data.message);

    // Current user object set karo
    currentUser = {
      name: data.name,
      email: data.email
    };

    // Local session save karo
    saveSession(currentUser);

    // Main application load karo
    loadApp();

  } catch(e) {

    // Server error case
    showToast('loginToast', 'Server error. Try again.');

  }
}

// ───────────────── SIGNUP FUNCTION ─────────────────

/*
doSignup()

Purpose:
Naya user account create karna.

Flow:
1. Signup form se data read karo
2. Form validation check karo
3. Backend register API call karo
4. Agar success ho:
      - session save karo
      - app load karo
*/

async function doSignup() {

  // Signup form values
  const name  = document.getElementById('signupName').value.trim();
  const email = document.getElementById('signupEmail').value.trim();
  const pass  = document.getElementById('signupPass').value;
  const pass2 = document.getElementById('signupPass2').value;

  // Validation checks
  if (!name || !email || !pass)
    return showToast('signupToast', 'Please fill all fields');

  if (pass !== pass2)
    return showToast('signupToast', 'Passwords do not match');

  if (pass.length < 6)
    return showToast('signupToast', 'Min 6 characters');

  try {

    // Backend register API
    const res = await fetch('/api/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },

      body: JSON.stringify({
        name,
        email,
        password: pass
      })
    });

    const data = await res.json();

    // Registration fail
    if (!data.success)
      return showToast('signupToast', data.message);

    // New user session set
    currentUser = {
      name: data.name,
      email: data.email
    };

    saveSession(currentUser);

    // Success message
    showToast('signupToast', 'Account created! Loading...', 'success');

    // App load delay
    setTimeout(loadApp, 800);

  } catch(e) {

    // Server error
    showToast('signupToast', 'Server error. Try again.');

  }
}




// ───────────── FORGOT PASSWORD FLOW ─────────────

// Step 1: User email enter karta hai aur system OTP generate karta hai
// Purana — LocalStorage check karta tha
// Naya — Backend se:
async function sendOtp() {
  const email = document.getElementById("forgotEmail").value.trim();
  if (!email) return showToast("forgotToast", "Enter your email");
  
  const res = await fetch('/api/auth/forgot', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email })
  });
  const data = await res.json();
  if (!data.success) return showToast("forgotToast", data.message);
  
  otpEmail = email;
  showToast("forgotToast", "OTP sent to your email!", "success");
  setTimeout(() => {
    document.getElementById("fStep1").style.display = "none";
    document.getElementById("fStep2").style.display = "block";
  }, 1200);
}


// ───────────── OTP INPUT AUTO-FOCUS ─────────────

/*
Purpose:
User jab OTP ka ek digit type kare
to cursor automatically next box me move ho jaye
*/
function otpNext(el, idx) {
  if (el.value && idx < 5)  // 3 ki jagah 5
    document.querySelectorAll(".otp-input")[idx + 1].focus();
}



// ───────────── OTP VERIFICATION ─────────────

function verifyOtp() {

  // Saare OTP input boxes ka value read karke ek string banate hain
  const entered = [...document.querySelectorAll(".otp-input")]
    .map((i) => i.value)
    .join("");

  // Agar entered OTP generated OTP se match nahi karta
  if (entered !== generatedOtp)
    return showToast("forgotToast", "Invalid OTP. Try again.");

  // OTP correct hai to Step2 hide aur Step3 (password reset) show
  document.getElementById("fStep2").style.display = "none";
  document.getElementById("fStep3").style.display = "block";
}



// ───────────── PASSWORD RESET ─────────────

function resetPass() {

  // New password fields read karo
  const p1 = document.getElementById("newPass").value;
  const p2 = document.getElementById("newPass2").value;

  // Agar passwords match nahi karte
  if (!p1 || p1 !== p2)
    return showToast("forgotToast", "Passwords do not match");

  // Minimum password length validation
  if (p1.length < 6)
    return showToast("forgotToast", "Min 6 characters");

  // LocalStorage se users load karo
  const users = getUsers();

  // Password update karo (Base64 encoding use ho rahi hai)
  users[otpEmail].password = btoa(p1);

  // Updated users data save karo
  saveUsers(users);

  // Success message show karo
  showToast("forgotToast", "Password updated! Redirecting...", "success");

  // 1.5 second baad login page par redirect
  setTimeout(() => showPage("loginPage"), 1500);
}



// ───────────── LOGOUT FUNCTION ─────────────

function doLogout() {

  // Current session remove karo
  localStorage.removeItem("fq_session");

  // Current user variable reset
  currentUser = null;

  // Main app interface hide
  document.getElementById("app").style.display = "none";

  // Agar typing animation chal rahi ho to stop karo
  if (typingTimer) clearTimeout(typingTimer);

  // Login page show karo
  showPage("loginPage");
}

// ───────────────── SETTINGS UPDATE ─────────────────

/*
saveSettings()

Purpose:
User apna profile update kar sakta hai.

User update kar sakta hai:
• Name
• Password

Flow:
1. Form values read karo
2. Validation check karo
3. Backend update API call karo
4. Success hone par UI update karo
*/

async function saveSettings() {

  // Settings form values
  const name  = document.getElementById('setName').value.trim();
  const pass  = document.getElementById('setPass').value;
  const pass2 = document.getElementById('setPass2').value;

  // Name validation
  if (!name)
    return showToast('setToast', 'Name cannot be empty');

  // Password match validation
  if (pass && pass !== pass2)
    return showToast('setToast', 'Passwords do not match');

  // Password length validation
  if (pass && pass.length < 6)
    return showToast('setToast', 'Min 6 characters');

  try {

    // Backend update API
    const res  = await fetch('/api/auth/update', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },

      body: JSON.stringify({
        email: currentUser.email,
        name,
        password: pass || ''
      })
    });

    const data = await res.json();

    // Update fail
    if (!data.success)
      return showToast('setToast', data.message);

    // Update current session
    currentUser = {
      name: data.name,
      email: data.email
    };

    saveSession(currentUser);

    // UI update
    document.getElementById('userNm').textContent = currentUser.name;
    document.getElementById('userAv').textContent =
      currentUser.name[0].toUpperCase();

    // Success message
    showToast('setToast', 'Saved successfully!', 'success');

  } catch(e) {

    // Server error
    showToast('setToast', 'Server error. Try again.');

  }
}

/* ───────────────── APP LOAD & NAVIGATION ───────────────── */

/*
loadApp()

Purpose:
User login hone ke baad main application dashboard load karna.

Steps:
1. Current session check karo
2. Login / signup pages hide karo
3. Main app interface show karo
4. User information UI me fill karo
5. Typing animation start karo
6. Database records load karo
*/
function loadApp() {

  // Current user session read karo (memory ya localStorage se)
  const s = currentUser || getSession();

  // Agar session nahi hai to function stop
  if (!s) return;

  // Current user set karo
  currentUser = s;

  // Authentication pages hide karo
  ["loginPage", "signupPage", "forgotPage"].forEach((p) => {
    document.getElementById(p).style.display = "none";
  });

  // Main application container show karo
  document.getElementById("app").style.display = "block";

  // Navbar me user ka name show karo
  document.getElementById("userNm").textContent = currentUser.name;

  // Avatar me user name ka first letter show hota hai
  document.getElementById("userAv").textContent =
    currentUser.name[0].toUpperCase();

  // Settings page me user info autofill
  document.getElementById("setName").value = currentUser.name;
  document.getElementById("setEmail").value = currentUser.email;

  // Dashboard welcome typing animation start
  startTyping();

  // Initial database data silently load karo
  fetchAll(true);
}



/*
switchSec()

Purpose:
Application ke different sections ke beech navigation handle karna.

Example sections:
• Dashboard
• Explorer
• Console
• Analytics
*/
function switchSec(name, el) {

  // Sab sections se active class remove karo
  document
    .querySelectorAll(".section")
    .forEach((s) => s.classList.remove("active"));

  // Navigation buttons reset karo
  document
    .querySelectorAll(".tnav")
    .forEach((n) => n.classList.remove("active"));

  // Selected section activate karo
  document.getElementById("sec-" + name).classList.add("active");

  // Clicked navigation item highlight karo
  el.classList.add("active");
}



/* ───────────────── API HELPER FUNCTIONS ───────────────── */

/*
setLoad()

Purpose:
Loading animation show / hide karna.

Parameters:
id → loader element id
on → true = loader start, false = loader stop
*/
function setLoad(id, on) {

  // CSS class "on" toggle karke loader control hota hai
  document.getElementById(id).classList.toggle("on", on);
}



/*
renderTable()

Purpose:
Backend API se aane wale raw data ko HTML table me convert karna.

Input format:
CSV style data
Example:
1,John,john@email.com

Output:
Styled HTML table
*/
function renderTable(data) {

  // Agar data empty hai
  if (!data || !data.trim() || data.includes("empty") || data.includes("Empty"))
    return `<div class="empty"><div class="empty-icon">◈</div>No records found.</div>`;

  // Data ko rows me split karo
  const rows = data
    .trim()
    .split("\n")
    .filter((r) => r.includes(","));

  // Agar valid rows nahi hain
  if (!rows.length)
    return `<div class="empty"><div class="empty-icon">◈</div>${data}</div>`;

  // Table structure create karo
  let html = `<table class="db-table">
  <thead>
  <tr>
  <th>#</th>
  <th>ID</th>
  <th>Username</th>
  <th>Email</th>
  </tr>
  </thead>
  <tbody>`;

  // Har row ko parse karke table me add karo
  rows.forEach((row, i) => {

    const p = row.split(",");

    if (p.length >= 3)
      html += `
      <tr>
      <td>${i + 1}</td>
      <td>${p[0].trim()}</td>
      <td>${p[1].trim()}</td>
      <td>${p[2].trim()}</td>
      </tr>`;
  });

  // Table close
  return html + "</tbody></table>";
}



/*
countRows()

Purpose:
Database response me total number of records count karna.

Use:
Dashboard statistics update karne ke liye
*/
function countRows(data) {

  // Empty data check
  if (!data || !data.trim() || data.includes("empty"))
    return 0;

  // Valid rows count karo
  return data
    .trim()
    .split("\n")
    .filter((r) => r.includes(",")).length;
}
/*
operate(type)

Purpose:
Database CRUD operations perform karna through backend API.

Supported operations:
• insert
• delete
• search

Flow:
1. Form inputs read karo
2. Validation karo
3. API URL build karo
4. Request send karo
5. Response UI me show karo
6. Execution time measure karo
*/
async function operate(type) {

  // Input fields se values read karo
  const id = document.getElementById("opId").value;
  const name = document.getElementById("opName").value;
  const email = document.getElementById("opEmail").value;

  // ID validation (ID positive hona chahiye)
  if (!id || id <= 0) {
    alert("Valid ID required");
    return;
  }

  // Base API URL
  let url = `/api/${type}?id=${id}`;

  // Agar operation INSERT hai to name aur email bhi required hain
  if (type === "insert") {

    if (!name || !email) {
      alert("Name and Email required");
      return;
    }

    // URL me encoded parameters add karo
    url += `&name=${encodeURIComponent(name)}&email=${encodeURIComponent(email)}`;
  }

  // Loader start karo (UI me loading animation)
  setLoad("lb1", true);

  // Execution time measure karne ke liye timer start
  const t0 = performance.now();

  try {

    // Backend API request
    const res = await fetch(url);

    // Response text read karo
    const data = await res.text();

    // Execution time calculate karo
    const ms = (performance.now() - t0).toFixed(1);

    // Response success detect
    const ok = data.includes("Executed") || data.includes(",");

    // Error detect
    const err = data.includes("Error") || data.includes("Bridge");

    // Output panel me response show karo
    document.getElementById("outBody").innerHTML =
      `<div class="log">
      <span class="${err ? "log-err" : ok ? "log-ok" : "log-info"}">
      ${data.trim()}
      </span>
      <span class="log-time"> · ${ms}ms · ${type.toUpperCase()}</span>
      </div>`;

    // Dashboard stats me last operation show karo
    document.getElementById("statOp").textContent = type.toUpperCase();

    /*
    Agar operation INSERT ya DELETE successful hai
    to input fields clear karo aur data refresh karo
    */
    if ((type === "insert" || type === "delete") && ok) {

      document.getElementById("opId").value = "";
      document.getElementById("opName").value = "";
      document.getElementById("opEmail").value = "";

      // Short delay ke baad table refresh
      setTimeout(() => fetchAll(true), 600);
    }

  } catch (e) {

    // Network error handle karo
    document.getElementById("outBody").innerHTML =
      `<div class="log">
      <span class="log-err">
      Network Error: ${e.message}
      </span>
      </div>`;
  }

  // Loader stop karo
  setLoad("lb1", false);
}



/*
fetchAll(silent)

Purpose:
Database ke saare records fetch karna.

Parameter:
silent = true → UI loader show nahi hoga
silent = false → loader show hoga

Use cases:
• Dashboard load
• Data refresh after insert/delete
*/
async function fetchAll(silent = false) {

  // Agar silent mode nahi hai to loader show karo
  if (!silent) setLoad("lb1", true);

  try {

    // Backend se saare records fetch karo
    const res = await fetch("/api/all");

    // Response text read karo
    const data = await res.text();

    // Agar silent mode nahi hai to table render karo
    if (!silent)
      document.getElementById("outBody").innerHTML = renderTable(data);

    // Dashboard me total records update
    document.getElementById("statTotal").textContent = countRows(data);

  } catch (e) {

    // Error handle karo
    if (!silent)
      document.getElementById("outBody").innerHTML =
        `<div class="log">
        <span class="log-err">
        Error: ${e.message}
        </span>
        </div>`;
  }

  // Loader stop
  if (!silent) setLoad("lb1", false);
}

/*
fetchAllExplorer()

Purpose:
Explorer section ke liye database ke saare records load karna.

Difference:
Ye function specifically Explorer panel me data show karta hai
na ki main output panel me.
*/
async function fetchAllExplorer() {

  // Explorer loader start
  setLoad("lb2", true);

  try {

    // Backend API se saare records fetch karo
    const res = await fetch("/api/all");

    // Response text read karo
    const data = await res.text();

    // Explorer table me render karo
    document.getElementById("explorerBody").innerHTML = renderTable(data);

    // Dashboard statistics update (total rows)
    document.getElementById("statTotal").textContent = countRows(data);

  } catch (e) {

    // Error UI show karo
    document.getElementById("explorerBody").innerHTML =
      `<div class="log">
      <span class="log-err">Error: ${e.message}</span>
      </div>`;
  }

  // Loader stop
  setLoad("lb2", false);
}



/*
runRaw()

Purpose:
Console-style raw command system run karna.

User commands example:
insert,id,name,email
search,id
delete,id
all

Flow:
1. User command read karo
2. Command parse karo
3. API URL build karo
4. Backend request bhejo
5. Result console panel me show karo
*/
async function runRaw() {

  // Console input field se command read karo
  const cmd = document.getElementById("rawCmd").value.trim();

  // Empty command ignore
  if (!cmd) return;

  // Command ko comma se split karo
  const parts = cmd.split(",");

  // Command type detect karo
  const type = parts[0].toLowerCase();

  // Console loader start
  setLoad("lb3", true);

  // Execution time measure karne ke liye timer
  const t0 = performance.now();

  // API URL variable
  let url = "";

  // ALL / SELECT command
  if (type === "all" || type === "select")
    url = "/api/all";

  // SEARCH command
  else if (type === "search" && parts[1])
    url = `/api/search?id=${parts[1].trim()}`;

  // INSERT command
  else if (type === "insert" && parts.length >= 4)
    url = `/api/insert?id=${parts[1].trim()}&name=${encodeURIComponent(parts[2].trim())}&email=${encodeURIComponent(parts[3].trim())}`;

  // DELETE command
  else if (type === "delete" && parts[1])
    url = `/api/delete?id=${parts[1].trim()}`;

  // Unknown command case
  else {

    document.getElementById("consoleBody").innerHTML =
      `<div class="log">
      <span class="log-err">Unknown command</span>
      <br>
      <span class="log-time">
      insert,id,name,email | search,id | delete,id | all
      </span>
      </div>`;

    setLoad("lb3", false);
    return;
  }

  try {

    // Backend API request
    const res = await fetch(url);

    // Response text read karo
    const data = await res.text();

    // Execution time calculate karo
    const ms = (performance.now() - t0).toFixed(1);

    // Response success detect
    const ok = data.includes("Executed") || data.includes(",");

    // Error detect
    const err = data.includes("Error") || data.includes("Bridge");

    /*
    Agar response me table data hai
    to renderTable use karo
    warna simple log message show karo
    */
    const html =
      data.includes(",") && type !== "delete"
        ? renderTable(data)
        : `<div class="log">
        <span class="${err ? "log-err" : ok ? "log-ok" : "log-info"}">
        ${data.trim()}
        </span>
        <span class="log-time"> · ${ms}ms</span>
        </div>`;

    // Console output update
    document.getElementById("consoleBody").innerHTML = html;

    // Command input clear
    document.getElementById("rawCmd").value = "";

  } catch (e) {

    // Network error handle
    document.getElementById("consoleBody").innerHTML =
      `<div class="log">
      <span class="log-err">Error: ${e.message}</span>
      </div>`;
  }

  // Loader stop
  setLoad("lb3", false);
}

/* ── INIT ── */

// Page load hone par ye function run hota hai
window.onload = () => {

  // Page ko visible karna (initial flash hide karne ke liye use hota hai)
  document.body.style.visibility = "visible";

  // Local storage / session se user session lena
  const s = getSession();

  // Agar session mil gaya to user already logged in hai
  if (s) {
    currentUser = s;   // current user set karo
    loadApp();         // main app load karo
  } 
  else {
    // Agar session nahi mila to login page show karo
    showPage("loginPage");
  }

  /* ── Analytics Navigation Section ── */

  // Ye function tabs switch karta hai (dashboard / explorer / analytics)
  function switchSec(name, el) {

    // Saare sections se "active" class remove
    document
      .querySelectorAll(".section")
      .forEach((s) => s.classList.remove("active"));

    // Saare navigation buttons se "active" remove
    document
      .querySelectorAll(".tnav")
      .forEach((n) => n.classList.remove("active"));

    // Selected section ko active karo
    document.getElementById("sec-" + name).classList.add("active");

    // Click kiye gaye nav button ko active karo
    el.classList.add("active");

    // Agar analytics tab open hua hai to analytics data load karo
    if (name === "analytics") loadAnalytics();
  }
};



// ── ANALYTICS VARIABLES ─────────────────────────

// Chart.js ke chart objects store karne ke liye
// taaki reload par purane charts destroy ho sake
let chartDist = null, chartTime = null, chartTimeline = null;



// ── ANALYTICS DATA LOAD FUNCTION ─────────────────
async function loadAnalytics() {

  try {

    // Backend se query logs fetch karna
    const res  = await fetch('/api/logs');

    // Response ko JSON me convert karna
    const data = await res.json();



    /* ── TOP STATS UPDATE ── */

    // Total queries UI me show karna
    document.getElementById('an-total').textContent = data.totalQueries;

    // Average execution time show karna
    document.getElementById('an-avg').textContent =
      data.avgExecTime.toFixed(1) + 'ms';

    // Success rate percentage show karna
    document.getElementById('an-success').textContent =
      data.successRate.toFixed(1) + '%';



    /* ── QUERY TYPE DATA PREPARE ── */

    // Query types (insert, search, delete, etc.)
    const types  = Object.keys(data.typeCounts);

    // Har type ka count
    const counts = Object.values(data.typeCounts);

    // Chart ke colors
    const COLORS = ['#d4af37','#5ddf96','#ff8c7f','#6a9fd8'];



    /* ── GLOBAL CHART STYLE ── */

    // Chart.js ka default text color
    Chart.defaults.color = '#6a85a8';

    // Chart border color
    Chart.defaults.borderColor = 'rgba(212,175,55,0.1)';



    /* ── 1️⃣ QUERY DISTRIBUTION CHART ── */

    // Agar pehle se chart hai to delete karo
    if (chartDist) chartDist.destroy();

    // New bar chart create
    chartDist = new Chart(document.getElementById('chartDist'), {

      type: 'bar',

      data: {
        labels: types,     // query names
        datasets: [{
          data: counts,    // query counts
          backgroundColor: COLORS,
          borderRadius: 8,
          borderSkipped: false
        }]
      },

      options: {
        plugins: { legend: { display: false } },

        scales: {
          x: { grid: { color: 'rgba(212,175,55,0.05)' } },
          y: {
            grid: { color: 'rgba(212,175,55,0.05)' },
            ticks: { stepSize: 1 }
          }
        }
      }
    });



    /* ── 2️⃣ AVERAGE EXECUTION TIME CHART ── */

    // Har query type ka average execution time calculate
    const avgTimes = types.map(t => {

      // Same type ke logs filter
      const matching =
        (data.recentLogs || []).filter(l => l.type === t);

      if (!matching.length) return 0;

      // Average calculate
      return (
        matching.reduce((a, b) => a + b.ms, 0) / matching.length
      ).toFixed(1);
    });


    // Purana chart delete
    if (chartTime) chartTime.destroy();

    // Naya chart create
    chartTime = new Chart(document.getElementById('chartTime'), {

      type: 'bar',

      data: {
        labels: types,
        datasets: [{
          data: avgTimes,
          backgroundColor: COLORS,
          borderRadius: 8,
          borderSkipped: false
        }]
      },

      options: {
        plugins: { legend: { display: false } },

        scales: {
          x: { grid: { color: 'rgba(212,175,55,0.05)' } },
          y: { grid: { color: 'rgba(212,175,55,0.05)' } }
        }
      }
    });



    /* ── 3️⃣ EXECUTION TIMELINE CHART ── */

    // Recent queries ka data
    const recent = data.recentLogs || [];

    // Success = green point
    // Fail = red point
    const ptColors =
      recent.map(l => l.success ? '#2ecc71' : '#e74c3c');


    if (chartTimeline) chartTimeline.destroy();

    // Line chart create
    chartTimeline =
      new Chart(document.getElementById('chartTimeline'), {

      type: 'line',

      data: {

        // X-axis -> query number
        labels: recent.map((_, i) => i + 1),

        datasets: [{

          label: 'Exec Time (ms)',

          // Y-axis -> execution time
          data: recent.map(l => l.ms),

          borderColor: '#d4af37',
          backgroundColor: 'rgba(212,175,55,0.08)',

          // Points color
          pointBackgroundColor: ptColors,

          pointRadius: 6,

          // Smooth curve
          tension: 0.3,

          // Area fill
          fill: true
        }]
      },

      options: {

        plugins: { legend: { display: false } },

        scales: {

          x: {
            grid: { color: 'rgba(212,175,55,0.05)' },
            title: {
              display: true,
              text: 'Query #',
              color: '#6a85a8'
            }
          },

          y: {
            grid: { color: 'rgba(212,175,55,0.05)' },
            title: {
              display: true,
              text: 'ms',
              color: '#6a85a8'
            }
          }
        }
      }
    });

  } catch(e) {

    // Agar analytics load fail ho jaye
    console.error('Analytics error:', e);

  }
}
const API_BASE = "http://127.0.0.1:17431";
let currentTab = null;
let currentHostname = "";

function getHostname(url) {
  try { return new URL(url).hostname; } catch (e) { return ""; }
}

function simplifySiteName(hostname) {
  const parts = hostname.replace(/^www\./, "").split(".");
  return parts.length > 1 ? parts[parts.length - 2] : hostname;
}

function generatePassword(length = 12) {
  const upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  const lower = "abcdefghijklmnopqrstuvwxyz";
  const digits = "0123456789";
  const symbols = "!@#$()";
  const all = upper + lower + digits + symbols;
  function rand(max) {
    const arr = new Uint32Array(1);
    crypto.getRandomValues(arr);
    return arr[0] % max;
  }
  const chars = [
    upper[rand(upper.length)], lower[rand(lower.length)],
    digits[rand(digits.length)], symbols[rand(symbols.length)]
  ];
  for (let i = 4; i < length; i++) chars.push(all[rand(all.length)]);
  for (let i = chars.length - 1; i > 0; i--) {
    const j = rand(i + 1);
    [chars[i], chars[j]] = [chars[j], chars[i]];
  }
  return chars.join("");
}

async function loadEntries() {
  const entriesDiv = document.getElementById("entries");

  let statusRes;
  try {
    statusRes = await fetch(`${API_BASE}/status`);
  } catch (e) {
    entriesDiv.innerHTML = `<div class="muted">Can't reach the desktop app. Make sure it's running.</div>`;
    return false;
  }

  const status = await statusRes.json();
  if (!status.unlocked) {
    entriesDiv.innerHTML = `<div class="muted">Vault is locked. Unlock it in the desktop app.</div>`;
    return false;
  }

  const res = await fetch(`${API_BASE}/passwords?site=${encodeURIComponent(currentHostname)}`);
  const entries = await res.json();

  if (!Array.isArray(entries) || entries.length === 0) {
    entriesDiv.innerHTML = `<div class="muted">No saved logins for this site yet.</div>`;
    return true;
  }

  entriesDiv.innerHTML = "";
  entries.forEach(entry => {
    const div = document.createElement("div");
    div.className = "entry";

    const info = document.createElement("div");
    info.innerHTML = `<div class="entry-site"></div><div class="entry-user"></div>`;
    info.querySelector(".entry-site").textContent = entry.site;
    info.querySelector(".entry-user").textContent = entry.username;

    const btns = document.createElement("div");
    btns.className = "btn-group";

    const fillBtn = document.createElement("button");
    fillBtn.textContent = "Fill";
    fillBtn.addEventListener("click", () => {
      chrome.tabs.sendMessage(currentTab.id, {
        type: "FILL_CREDENTIALS",
        username: entry.username,
        password: entry.password
      });
      window.close();
    });

    const copyBtn = document.createElement("button");
    copyBtn.className = "ghost";
    copyBtn.textContent = "Copy";
    copyBtn.addEventListener("click", async () => {
      await navigator.clipboard.writeText(entry.password);
      copyBtn.textContent = "Copied!";
      setTimeout(() => copyBtn.textContent = "Copy", 1200);
    });

    btns.appendChild(fillBtn);
    btns.appendChild(copyBtn);
    div.appendChild(info);
    div.appendChild(btns);
    entriesDiv.appendChild(div);
  });
  return true;
}

async function init() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  currentTab = tab;
  currentHostname = getHostname(tab.url);

  document.getElementById("siteTag").textContent = currentHostname || "no site detected";
  document.getElementById("addSite").value = simplifySiteName(currentHostname);

  await loadEntries();

  document.getElementById("genBtn").addEventListener("click", () => {
    document.getElementById("addPassword").value = generatePassword(12);
  });

  document.getElementById("saveBtn").addEventListener("click", async () => {
    const site = document.getElementById("addSite").value.trim();
    const username = document.getElementById("addUsername").value.trim();
    const password = document.getElementById("addPassword").value;
    const statusDiv = document.getElementById("addStatus");

    if (!site || !username || !password) {
      statusDiv.textContent = "All fields required (hit \u2728 to generate a password).";
      statusDiv.className = "status err";
      return;
    }

    try {
      const res = await fetch(`${API_BASE}/save`, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: `site=${encodeURIComponent(site)}&username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}`
      });
      const data = await res.json();
      if (data.error) {
        statusDiv.textContent = data.error === "locked" ? "Unlock your vault first." : "Failed: " + data.error;
        statusDiv.className = "status err";
        return;
      }
      statusDiv.textContent = "Saved!";
      statusDiv.className = "status ok";
      document.getElementById("addUsername").value = "";
      document.getElementById("addPassword").value = "";
      loadEntries();
    } catch (e) {
      statusDiv.textContent = "Can't reach the desktop app.";
      statusDiv.className = "status err";
    }
  });
}

init();
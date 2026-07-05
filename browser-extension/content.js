const API_BASE = "http://127.0.0.1:17431";

let injectedIcon = null;
let injectedGenerateBtn = null;
let saveBanner = null;
let lastUrl = location.href;
let debounceTimer = null;
let knownSavedPassword = null;
let lastCaptureAt = 0;

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
    upper[rand(upper.length)],
    lower[rand(lower.length)],
    digits[rand(digits.length)],
    symbols[rand(symbols.length)]
  ];
  for (let i = 4; i < length; i++) chars.push(all[rand(all.length)]);

  for (let i = chars.length - 1; i > 0; i--) {
    const j = rand(i + 1);
    [chars[i], chars[j]] = [chars[j], chars[i]];
  }
  return chars.join("");
}

function getHostname() {
  return location.hostname;
}

function setNativeValue(element, value) {
  const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(element), "value").set;
  setter.call(element, value);
  element.dispatchEvent(new Event("input", { bubbles: true }));
  element.dispatchEvent(new Event("change", { bubbles: true }));
}

function findUsernameField(passwordField) {
  const form = passwordField.closest("form");
  const scope = form || document;
  const candidates = Array.from(scope.querySelectorAll('input[type="text"], input[type="email"], input:not([type])'));
  for (const input of candidates) {
    const attrs = `${input.name} ${input.id} ${input.autocomplete} ${input.placeholder}`.toLowerCase();
    if (["username", "email", "user", "login"].some(k => attrs.includes(k))) return input;
  }
  return candidates[0] || null;
}

function simplifySiteName(hostname) {
  return hostname.replace(/^www\./, "").split(".")[0];
}

function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str;
  return div.innerHTML;
}

async function checkForSavedLogin() {
  removeAll();

  const passwordField = document.querySelector('input[type="password"]');
  if (!passwordField) return;

  let statusRes;
  try {
    statusRes = await fetch(`${API_BASE}/status`);
  } catch (e) {
    return;
  }
  const status = await statusRes.json();
  if (!status.unlocked) return;

  let entries = [];
  try {
    const res = await fetch(`${API_BASE}/passwords?site=${encodeURIComponent(getHostname())}`);
    const data = await res.json();
    if (Array.isArray(data)) entries = data;
  } catch (e) {
    entries = [];
  }

  let offset = 0;
  if (entries.length > 0) {
    injectKeyIcon(passwordField, entries, offset);
    offset++;
  }
  injectGenerateIcon(passwordField, offset);

  attachSubmitCapture(passwordField);
}

function injectKeyIcon(passwordField, entries, offset) {
  const icon = makeFloatingIcon("\u{1F511}", "Fill saved login", passwordField, offset);
  icon.addEventListener("click", (e) => {
    e.preventDefault();
    e.stopPropagation();
    showEntryMenu(icon, passwordField, entries);
  });
  injectedIcon = icon;
}

function injectGenerateIcon(passwordField, offset) {
  const icon = makeFloatingIcon("\u2728", "Generate a strong password", passwordField, offset);
  icon.addEventListener("click", (e) => {
    e.preventDefault();
    e.stopPropagation();
    const newPassword = generatePassword(12);
    setNativeValue(passwordField, newPassword);
    showSaveBanner(passwordField, newPassword);
  });
  injectedGenerateBtn = icon;
}

function makeFloatingIcon(symbol, title, field, offsetIndex) {
  const icon = document.createElement("div");
  icon.textContent = symbol;
  icon.title = title;
  Object.assign(icon.style, {
    position: "absolute",
    cursor: "pointer",
    fontSize: "16px",
    zIndex: "999999",
    background: "#24283b",
    border: "1px solid #7aa2f7",
    borderRadius: "6px",
    padding: "2px 6px",
    boxShadow: "0 0 8px rgba(122,162,247,0.5)"
  });
  positionIcon(icon, field, offsetIndex);
  document.body.appendChild(icon);

  const reposition = () => positionIcon(icon, field, offsetIndex);
  window.addEventListener("scroll", reposition, true);
  window.addEventListener("resize", reposition);
  return icon;
}

function positionIcon(icon, field, offsetIndex) {
  const rect = field.getBoundingClientRect();
  icon.style.top = `${window.scrollY + rect.top + (rect.height / 2) - 11}px`;
  icon.style.left = `${window.scrollX + rect.right + 6 + (offsetIndex * 34)}px`;
}

function showEntryMenu(icon, passwordField, entries) {
  removeMenu();
  const menu = document.createElement("div");
  menu.id = "pwvault-menu";
  Object.assign(menu.style, {
    position: "absolute",
    zIndex: "1000000",
    background: "#24283b",
    border: "1px solid #2f334d",
    borderRadius: "8px",
    boxShadow: "0 8px 24px rgba(0,0,0,0.4)",
    padding: "6px",
    minWidth: "180px",
    fontFamily: "Arial, sans-serif"
  });
  const rect = icon.getBoundingClientRect();
  menu.style.top = `${window.scrollY + rect.bottom + 4}px`;
  menu.style.left = `${window.scrollX + rect.left}px`;

  entries.forEach(entry => {
    const item = document.createElement("div");
    item.textContent = entry.username;
    Object.assign(item.style, {
      padding: "8px 10px",
      cursor: "pointer",
      color: "#c0caf5",
      fontSize: "13px",
      borderRadius: "5px"
    });
    item.addEventListener("mouseenter", () => item.style.background = "#2f334d");
    item.addEventListener("mouseleave", () => item.style.background = "transparent");
    item.addEventListener("click", () => {
      const usernameField = findUsernameField(passwordField);
      if (usernameField) setNativeValue(usernameField, entry.username);
      setNativeValue(passwordField, entry.password);
      knownSavedPassword = entry.password;
      removeMenu();
    });
    menu.appendChild(item);
  });

  document.body.appendChild(menu);
  setTimeout(() => document.addEventListener("click", outsideClickListener), 0);
}

function outsideClickListener(e) {
  const menu = document.getElementById("pwvault-menu");
  if (menu && !menu.contains(e.target)) removeMenu();
}

function removeMenu() {
  const menu = document.getElementById("pwvault-menu");
  if (menu) menu.remove();
  document.removeEventListener("click", outsideClickListener);
}

function removeAll() {
  if (injectedIcon) { injectedIcon.remove(); injectedIcon = null; }
  if (injectedGenerateBtn) { injectedGenerateBtn.remove(); injectedGenerateBtn = null; }
  removeMenu();
}

function attachSubmitCapture(passwordField) {
  if (passwordField.dataset.pwvaultCaptureAttached) return;
  passwordField.dataset.pwvaultCaptureAttached = "true";

  const form = passwordField.closest("form");
  if (form && !form.dataset.pwvaultCaptureAttached) {
    form.dataset.pwvaultCaptureAttached = "true";
    form.addEventListener("submit", () => captureAndOfferSave(passwordField), true);
  }

  passwordField.addEventListener("keydown", (e) => {
    if (e.key === "Enter") captureAndOfferSave(passwordField);
  });
}

function captureAndOfferSave(passwordField) {
  const now = Date.now();
  if (now - lastCaptureAt < 500) return;
  lastCaptureAt = now;

  const password = passwordField.value;
  if (!password || password === knownSavedPassword) return;

  const usernameField = findUsernameField(passwordField);
  const username = usernameField ? usernameField.value : "";
  if (!username) return;

  setTimeout(() => showSaveBanner(passwordField, password, username), 50);
}

function showSaveBanner(passwordField, password, prefilledUsername) {
  if (saveBanner) saveBanner.remove();

  const usernameField = findUsernameField(passwordField);
  const defaultUsername = prefilledUsername || (usernameField ? usernameField.value : "");
  const defaultSite = simplifySiteName(getHostname());

  const banner = document.createElement("div");
  banner.id = "pwvault-banner";
  Object.assign(banner.style, {
    position: "fixed",
    top: "16px",
    right: "16px",
    zIndex: "1000001",
    background: "#24283b",
    border: "1px solid #2f334d",
    borderRadius: "10px",
    boxShadow: "0 8px 24px rgba(0,0,0,0.5)",
    padding: "14px",
    width: "260px",
    fontFamily: "Arial, sans-serif",
    color: "#c0caf5"
  });

  banner.innerHTML = `
    <div style="font-size:13px;font-weight:bold;color:#fff;margin-bottom:8px;">Save to Password Vault?</div>
    <input id="pwvault-site" type="text" value="${escapeHtml(defaultSite)}" placeholder="Site name"
      style="width:100%;box-sizing:border-box;margin-bottom:6px;padding:6px 8px;background:#1f2335;border:1px solid #2f334d;border-radius:6px;color:#c0caf5;font-size:12px;" />
    <input id="pwvault-username" type="text" value="${escapeHtml(defaultUsername)}" placeholder="Username"
      style="width:100%;box-sizing:border-box;margin-bottom:10px;padding:6px 8px;background:#1f2335;border:1px solid #2f334d;border-radius:6px;color:#c0caf5;font-size:12px;" />
    <div style="display:flex;gap:8px;">
      <button id="pwvault-save" style="flex:1;background:#7aa2f7;color:#1a1b26;border:none;border-radius:6px;padding:7px;font-size:12px;font-weight:bold;cursor:pointer;">Save</button>
      <button id="pwvault-dismiss" style="flex:1;background:transparent;color:#9aa5ce;border:1px solid #2f334d;border-radius:6px;padding:7px;font-size:12px;cursor:pointer;">Not now</button>
    </div>
    <div id="pwvault-banner-status" style="font-size:11px;margin-top:6px;"></div>
  `;

  document.body.appendChild(banner);
  saveBanner = banner;

  document.getElementById("pwvault-dismiss").addEventListener("click", () => {
    banner.remove();
    saveBanner = null;
  });

  document.getElementById("pwvault-save").addEventListener("click", async () => {
    const site = document.getElementById("pwvault-site").value.trim();
    const username = document.getElementById("pwvault-username").value.trim();
    const statusDiv = document.getElementById("pwvault-banner-status");

    if (!site || !username) {
      statusDiv.textContent = "Site and username required.";
      statusDiv.style.color = "#f7768e";
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
        statusDiv.style.color = "#f7768e";
        return;
      }
      statusDiv.textContent = "Saved!";
      statusDiv.style.color = "#9ece6a";
      knownSavedPassword = password;
      setTimeout(() => {
        banner.remove();
        saveBanner = null;
      }, 1000);
    } catch (e) {
      statusDiv.textContent = "Can't reach the desktop app.";
      statusDiv.style.color = "#f7768e";
    }
  });
}

chrome.runtime.onMessage.addListener((message) => {
  if (message.type !== "FILL_CREDENTIALS") return;
  const passwordField = document.querySelector('input[type="password"]');
  if (!passwordField) return;
  const usernameField = findUsernameField(passwordField);
  if (usernameField) setNativeValue(usernameField, message.username);
  setNativeValue(passwordField, message.password);
  knownSavedPassword = message.password;
});

function scheduleCheck() {
  clearTimeout(debounceTimer);
  debounceTimer = setTimeout(checkForSavedLogin, 400);
}

checkForSavedLogin();

const observer = new MutationObserver(() => {
  if (location.href !== lastUrl) {
    lastUrl = location.href;
    scheduleCheck();
  } else if ((!injectedIcon && !injectedGenerateBtn) ||
             (injectedIcon && !document.body.contains(injectedIcon)) ||
             (injectedGenerateBtn && !document.body.contains(injectedGenerateBtn))) {
    scheduleCheck();
  }
});
observer.observe(document.body, { childList: true, subtree: true });
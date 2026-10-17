#pragma once

static const char INDEX_HTML[] = R"HTML(<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>CatFinder</title>
<style>
  :root { color-scheme:light dark; --bg:#f2f3f5; --card:#fff; --fg:#1b1d21; --dim:#6a7079; --line:#d8dbe0;
          --field:#fff; --btn:#e6e8ec; --accent:#2563eb; --onacc:#fff; --ok:#16a34a; --bad:#dc2626;
          --pad:#e8ebf0; --knob:#8b94a3; }
  @media (prefers-color-scheme:dark) {
    :root { --bg:#101215; --card:#1a1d22; --fg:#e8e9ec; --dim:#9aa1ab; --line:#2f333a; --field:#12141a;
            --btn:#2b2f37; --accent:#6d9bff; --onacc:#0b1220; --ok:#3ecf6e; --bad:#e5484d;
            --pad:#22262d; --knob:#586070; }
  }
  * { box-sizing:border-box; }
  [hidden] { display:none !important; }
  body { margin:0; background:var(--bg); color:var(--fg);
         font:16px/1.45 system-ui,-apple-system,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif; }
  header { display:flex; justify-content:space-between; align-items:center; padding:10px 16px;
           background:var(--card); border-bottom:1px solid var(--line); }
  h1 { font-size:20px; margin:0; }
  h3 { font-size:15px; margin:0 0 8px; }
  p { margin:0 0 8px; }
  .conn { display:flex; align-items:center; gap:8px; color:var(--dim); font-size:14px; }
  .dot { width:10px; height:10px; border-radius:50%; background:var(--dim); }
  .dot.ok { background:var(--ok); }
  .dot.bad { background:var(--bad); }
  main { display:grid; grid-template-columns:minmax(0,1fr); gap:16px; padding:16px; max-width:1100px; margin:0 auto; }
  @media (min-width:760px), (orientation:landscape) and (min-width:560px) {
    main { grid-template-columns:minmax(0,1fr) 280px; }
    details { grid-column:1/-1; }
  }
  @media (max-height:500px) and (orientation:landscape) {
    header { padding:6px 12px; }
    main { padding:8px; gap:8px; grid-template-columns:minmax(0,1fr) 240px; }
    .ctl { padding:10px; gap:8px; }
  }
  @media (max-width:420px) { main { padding:12px; } .body { padding:8px 12px 14px; } }
  .card { background:var(--card); border:1px solid var(--line); border-radius:12px; }

  .video { position:relative; background:#000; border-radius:12px; overflow:hidden; aspect-ratio:4/3;
           max-height:calc(100vh - 100px); width:100%; }
  #stream { position:absolute; inset:0; width:100%; height:100%; object-fit:contain; }
  #vmsg { position:absolute; inset:0; display:flex; align-items:center; justify-content:center;
          text-align:center; padding:16px; color:#fff; background:rgba(0,0,0,.55); font-size:18px; }
  .vbar { margin-top:8px; }

  .ctl { display:flex; flex-direction:column; align-items:center; gap:14px; padding:16px; align-self:start; }
  #pad { position:relative; width:min(240px,62vw,max(140px,40vh)); aspect-ratio:1; flex:none; border-radius:50%;
         background:var(--pad); border:2px solid var(--line); touch-action:none; cursor:grab;
         user-select:none; -webkit-user-select:none; -webkit-touch-callout:none; }
  #pad::before, #pad::after { content:''; position:absolute; background:var(--line); }
  #pad::before { left:50%; top:10%; bottom:10%; width:1px; }
  #pad::after { top:50%; left:10%; right:10%; height:1px; }
  #knob { position:absolute; width:28%; aspect-ratio:1; left:50%; top:50%; transform:translate(-50%,-50%);
          border-radius:50%; background:var(--knob); pointer-events:none; z-index:1; }
  #pad.active #knob { background:var(--ok); }
  .speed { width:100%; display:flex; flex-direction:column; gap:4px; font-size:15px; }
  input[type=range] { width:100%; height:32px; accent-color:var(--accent); }
  #stop { width:100%; padding:14px; font-size:22px; font-weight:700; background:var(--bad); color:#fff; border-radius:12px; }
  .hint { color:var(--dim); font-size:13px; margin:6px 0 0; }
  .ctl .hint { text-align:center; margin:0; }

  button { font:inherit; background:var(--btn); color:var(--fg); border:0; border-radius:8px; padding:8px 14px;
           min-height:40px; cursor:pointer; }
  button:disabled { opacity:.5; cursor:default; }
  button.on { background:var(--ok); color:#000; }
  .primary { background:var(--accent); color:var(--onacc); }

  details.card > summary { padding:14px 16px; font-weight:600; font-size:17px; cursor:pointer; }
  details.card[open] > summary { border-bottom:1px solid var(--line); }
  .body { padding:8px 16px 16px; }
  .sub { margin-top:20px; }
  .sub:first-child { margin-top:8px; }
  .scroll { overflow-x:auto; }
  table { border-collapse:collapse; }
  #cfg { width:100%; }
  #cfg td, #cfg th { padding:5px 8px 5px 0; vertical-align:middle; text-align:left; font-size:14px; }
  #cfg th { color:var(--dim); font-weight:500; }
  #cfg tr.n td { padding-top:0; color:var(--dim); font-size:12px; }
  input[type=number], select { background:var(--field); color:var(--fg); border:1px solid var(--line);
                               border-radius:6px; padding:6px; font:inherit; min-height:36px; }
  #cfg input[type=number] { width:84px; }
  #cfg input[type=checkbox] { width:20px; height:20px; }
  .tests { display:flex; gap:4px; }
  .tests button { padding:4px 10px; min-height:36px; }
  .cfg-foot { display:flex; gap:8px; align-items:center; margin-top:8px; flex-wrap:wrap; }
  #cfgmsg { color:var(--dim); font-size:14px; }
  .cal-row { display:flex; gap:6px; align-items:center; flex-wrap:wrap; margin-bottom:8px; }
  #calrun.on { background:var(--ok); color:#000; }
  #calval { min-width:150px; font-size:14px; }
  #caltab td { text-align:right; padding:2px 10px 2px 0; font-size:13px; }
  #status { width:100%; }
  #status td { padding:3px 8px 3px 0; vertical-align:top; font-size:13px; word-break:break-word; }
  #status td:first-child { color:var(--dim); white-space:nowrap; }
  pre { margin:0; max-height:260px; overflow:auto; font:12px/1.4 ui-monospace,Menlo,Consolas,monospace;
        white-space:pre-wrap; word-break:break-all; background:var(--field); border:1px solid var(--line);
        border-radius:8px; padding:8px; }
  progress { width:100%; }
</style>
</head>
<body>
<header>
  <h1>CatFinder</h1>
  <div class="conn" role="status"><i class="dot" id="dot"></i><span id="connt">Connecting…</span></div>
</header>
<main>
  <section>
    <div class="video">
      <img id="stream" alt="" role="img" aria-label="Live video from CatFinder">
      <div id="vmsg">Starting camera…</div>
    </div>
    <div class="vbar"><button id="reload">Reconnect video</button></div>
  </section>

  <section class="card ctl">
    <div id="pad"><div id="knob"></div></div>
    <label class="speed"><span>Max speed: <b><span id="maxv"></span>%</b></span>
      <input id="max" type="range" min="10" max="100" step="5" value="60"></label>
    <button id="stop">Stop</button>
    <p class="hint">Drag the circle to drive. On a computer: arrow keys or WASD, space to stop.</p>
  </section>

  <details class="card" id="setup">
    <summary>Setup</summary>
    <div class="body">
      <section class="sub">
        <h3>Wheels</h3>
        <div class="scroll"><table id="cfg">
          <tr><th></th><th id="hA">Servo A</th><th id="hB">Servo B</th></tr>
          <tr><td>Which wheel</td>
            <td><select id="sideA"><option value="0">left</option><option value="1">right</option></select></td>
            <td id="sideB"></td></tr>
          <tr><td>Reverse direction</td>
            <td><input type="checkbox" id="inv_a" aria-label="Reverse direction, servo A"></td>
            <td><input type="checkbox" id="inv_b" aria-label="Reverse direction, servo B"></td></tr>
          <tr><td>Stop point (µs)</td>
            <td><input type="number" id="stop_a" min="1000" max="2000" step="5"></td>
            <td><input type="number" id="stop_b" min="1000" max="2000" step="5"></td></tr>
          <tr class="n"><td colspan="3">Adjust until the wheel stands still during the ■ test.</td></tr>
          <tr><td>Test</td>
            <td><div class="tests" data-servo="0"></div></td>
            <td><div class="tests" data-servo="1"></div></td></tr>
          <tr><td>Top speed range (µs)</td><td colspan="2"><input type="number" id="span" min="50" max="1000" step="10"></td></tr>
          <tr><td>Safety stop after (ms)</td><td colspan="2"><input type="number" id="timeout" min="100" max="5000" step="50"></td></tr>
        </table></div>
        <div class="cfg-foot"><button id="cfgreset">Reset to defaults</button><span id="cfgmsg"></span></div>
        <p class="hint">Changes are saved immediately. The test buttons turn one wheel on its own:
          ◀ and ▶ at half speed, ■ at the stop point. Press again to end the test.</p>
      </section>

      <section class="sub">
        <h3>Drive straight</h3>
        <p class="hint">Two wheels rarely turn at exactly the same speed. Pick a speed, press Run, and watch which way the robot drifts.</p>
        <p class="hint">Press + or − until it goes straight: + slows the left wheel, − slows the right wheel. Press Run again to stop. Changes are saved immediately.</p>
        <div class="cal-row" id="callevels" style="margin-top:10px"></div>
        <div class="cal-row"><button id="calrun">Run</button><span id="calval"></span></div>
        <div class="cal-row" id="calsteps"></div>
        <table id="caltab"></table>
      </section>

      <section class="sub">
        <h3>WiFi</h3>
        <p>Current network: <b id="wifiname">…</b></p>
        <button id="wifichg">Change WiFi network</button>
        <p class="hint" id="wifimsg"></p>
      </section>

      <section class="sub">
        <h3>Firmware update</h3>
        <p>Installed version: <b id="fwver">…</b></p>
        <p><input type="file" id="fwfile" accept=".bin"></p>
        <p><button id="fwgo" class="primary">Install</button></p>
        <progress id="fwprog" max="100" value="0" hidden></progress>
        <p class="hint" id="fwmsg"></p>
        <p class="hint">Download the newest catfinder-X.Y.Z.bin from the GitHub releases page (not the -factory file).</p>
      </section>
    </div>
  </details>

  <details class="card" id="details">
    <summary>Details</summary>
    <div class="body">
      <table id="status"></table>
      <p class="hint">Last drive command: <span id="drv">L 0 · R 0</span></p>
      <h3 style="margin-top:16px">Log</h3>
      <pre id="log"></pre>
    </div>
  </details>
</main>
<script>
const $ = id => document.getElementById(id);
const esc = s => String(s).replace(/[&<>"']/g, c => '&#' + c.charCodeAt(0) + ';');

// --- video -----------------------------------------------------------------
const streamUrl = `${location.protocol}//${location.hostname}:81/stream`;
const img = $('stream'), vmsg = $('vmsg');
let gotFrame = false, retry = 0;
function showVmsg() {
  vmsg.textContent = gotFrame ? 'Video lost, reconnecting…' : 'Starting camera…';
  vmsg.hidden = false;
}
function startStream() { clearTimeout(retry); img.src = `${streamUrl}?t=${Date.now()}`; }
img.onload = () => { gotFrame = true; vmsg.hidden = true; };
img.onerror = () => { showVmsg(); clearTimeout(retry); retry = setTimeout(startStream, 2000); };
$('reload').onclick = () => { gotFrame = false; showVmsg(); startStream(); };
startStream();

// --- status and log --------------------------------------------------------
const fmtUptime = s => {
  const d = Math.floor(s / 86400), h = Math.floor(s / 3600) % 24, m = Math.floor(s / 60) % 60;
  return `${d ? d + 'd ' : ''}${h}h ${m}m ${s % 60}s`;
};
const kb = b => (b / 1024).toFixed(1) + ' KB';

const rows = s => [
  ['Version', s.version],
  ['Build', s.build],
  ['Partition', s.partition + ' (' + s.ota_state + ')'],
  ['Uptime', fmtUptime(s.uptime_s)],
  ['Reset reason', s.reset_reason],
  ['WiFi network', s.ssid],
  ['IP', s.ip],
  ['RSSI', s.rssi + ' dBm (ch ' + s.channel + ')'],
  ['WiFi reconnects', s.wifi_reconnects],
  ['Heap free / min', kb(s.heap_free) + ' / ' + kb(s.heap_min)],
  ['PSRAM free', s.psram_size ? kb(s.psram_free) + ' of ' + kb(s.psram_size) : 'none'],
  ['Sketch / free slot', kb(s.sketch_size) + ' / ' + kb(s.sketch_free)],
  ['CPU', s.cpu_mhz + ' MHz, chip rev ' + s.chip_rev],
  ['Camera', s.cam_ok ? s.cam_sensor + ', ' + s.cam_framesize : 'FAILED'],
  ['Stream clients', s.stream_clients],
  ['Stream FPS', Number(s.stream_fps).toFixed(1)],
  ['Frame size', kb(s.frame_bytes)],
  ['Frames sent', s.frames_sent],
  ['Drive L / R', s.drive_l + ' / ' + s.drive_r],
];

let polling = false;
let upd = 0;  // after a firmware update: 1 = wait for the robot to go away, 2 = reload when it is back
async function poll() {
  if (polling) return;
  polling = true;
  if (img.complete && img.naturalWidth) { gotFrame = true; vmsg.hidden = true; }  // in case a browser skips onload for MJPEG
  let s = null;
  const ac = new AbortController(), t = setTimeout(() => ac.abort(), 3000);
  try { s = await (await fetch('/status', { cache: 'no-store', signal: ac.signal })).json(); } catch (e) {}
  clearTimeout(t);
  polling = false;
  $('dot').className = 'dot ' + (s ? 'ok' : 'bad');
  $('connt').textContent = s ? 'Connected' : 'Offline';
  if (!s) { if (upd === 1) upd = 2; return; }
  if (upd === 2) { location.reload(); return; }
  $('wifiname').textContent = s.ssid || '–';
  $('fwver').textContent = s.version || '–';
  try {
    $('status').innerHTML = rows(s).map(([k, v]) => `<tr><td>${k}</td><td>${esc(v)}</td></tr>`).join('');
  } catch (e) {}
}

const detEl = $('details');
async function pollLog() {
  if (!detEl.open) return;
  try {
    const r = await fetch('/log', { cache: 'no-store' });
    const el = $('log');
    const atBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 4;
    el.textContent = await r.text();
    if (atBottom) el.scrollTop = el.scrollHeight;
  } catch (e) {}
}
detEl.ontoggle = pollLog;

// --- drive settings ------------------------------------------------------
const cfgMsg = $('cfgmsg');
let cfg = null;
let test = null;  // {servo, kind, button} while a servo test runs

function showCfg(c) {
  cfg = c;
  $('hA').textContent = `Servo A (IO${c.pin_a})`;
  $('hB').textContent = `Servo B (IO${c.pin_b})`;
  $('sideA').value = c.swap;
  $('sideB').textContent = c.swap ? 'left' : 'right';
  $('inv_a').checked = !!c.inv_a;
  $('inv_b').checked = !!c.inv_b;
  for (const k of ['stop_a', 'stop_b', 'span', 'timeout']) $(k).value = c[k];
  showCal();
}

async function saveCfg(body) {
  cfgMsg.textContent = 'saving…';
  try {
    const r = await fetch('/drive_cfg', { method: 'POST', body: new URLSearchParams(body) });
    if (!r.ok) throw new Error(r.status);
    showCfg(await r.json());
    cfgMsg.textContent = 'saved';
  } catch (e) {
    cfgMsg.textContent = 'save failed';
  }
}

async function loadCfg() {
  try {
    showCfg(await (await fetch('/drive_cfg', { cache: 'no-store' })).json());
  } catch (e) {
    cfgMsg.textContent = 'could not load settings';
    setTimeout(loadCfg, 3000);
  }
}

$('sideA').onchange = e => saveCfg({ swap: e.target.value });
for (const k of ['inv_a', 'inv_b']) $(k).onchange = e => saveCfg({ [k]: e.target.checked ? 1 : 0 });
for (const k of ['stop_a', 'stop_b', 'span', 'timeout']) $(k).onchange = e => saveCfg({ [k]: e.target.value });
$('cfgreset').onclick = () => { if (confirm('Reset drive settings to the firmware defaults?')) saveCfg({ reset: 1 }); };

const TESTS = [['back', '◀'], ['stop', '■'], ['fwd', '▶']];
document.querySelectorAll('.tests').forEach(el => {
  const servo = +el.dataset.servo;
  for (const [kind, label] of TESTS) {
    const b = document.createElement('button');
    b.textContent = label;
    b.onclick = () => {
      const same = test && test.button === b;
      endTest();
      endCal();
      if (!same) { test = { servo, kind, button: b }; b.classList.add('on'); }
    };
    el.appendChild(b);
  }
});
function endTest() {
  if (!test) return;
  test.button.classList.remove('on');
  test = null;
  send(0, 0);  // a drive command cancels the test on the board right away
}
function testUs() {
  const stop = cfg[test.servo ? 'stop_b' : 'stop_a'];
  return test.kind === 'stop' ? stop : stop + (test.kind === 'fwd' ? 1 : -1) * Math.round(cfg.span / 2);
}

// --- straight-line calibration ---------------------------------------------
const CAL_LEVELS = [25, 50, 75, 100];
let calIdx = 0;        // selected calibration point
let calRun = false;    // both wheels driven at CAL_LEVELS[calIdx] while true

function showCal() {
  document.querySelectorAll('#callevels button').forEach((b, i) => b.classList.toggle('on', i === calIdx));
  if (!cfg) return;
  const v = cfg.bal[calIdx];
  $('calval').textContent = `${v > 0 ? '+' : ''}${v} µs` + (v > 0 ? ' (left slowed)' : v < 0 ? ' (right slowed)' : '');
  $('caltab').innerHTML = '<tr>' + CAL_LEVELS.map(l => `<td>${l}%</td>`).join('') + '</tr><tr>' +
    cfg.bal.map(b => `<td>${b > 0 ? '+' : ''}${b}</td>`).join('') + '</tr>';
}
CAL_LEVELS.forEach((l, i) => {
  const b = document.createElement('button');
  b.textContent = l + '%';
  b.onclick = () => { calIdx = i; showCal(); };
  $('callevels').appendChild(b);
});
for (const d of [-5, -1, 1, 5]) {
  const b = document.createElement('button');
  b.textContent = (d > 0 ? '+' : '−') + Math.abs(d);
  b.onclick = () => { if (cfg) saveCfg({ ['bal' + calIdx]: cfg.bal[calIdx] + d }); };
  $('calsteps').appendChild(b);
}
$('calrun').onclick = () => { if (calRun) endCal(); else { endTest(); calRun = true; $('calrun').classList.add('on'); } };
function endCal() {
  if (!calRun) return;
  calRun = false;
  $('calrun').classList.remove('on');
  send(0, 0);
}
showCal();

// --- WiFi ------------------------------------------------------------------
$('wifichg').onclick = async () => {
  if (!confirm('The robot forgets this WiFi and restarts as the hotspot CatFinder-XXXX. Join that hotspot to choose a new network. Continue?')) return;
  stopAll();
  const m = $('wifimsg');
  try {
    const r = await fetch('/wifi/forget', { method: 'POST' });
    if (!r.ok) throw new Error(r.status);
    m.textContent = 'Restarting. Join the CatFinder hotspot on your phone or computer.';
  } catch (e) {
    m.textContent = 'No answer from the robot. If it restarted, join the CatFinder hotspot.';
  }
};

// --- firmware update -------------------------------------------------------
$('fwgo').onclick = () => {
  const f = $('fwfile').files[0], m = $('fwmsg'), p = $('fwprog'), b = $('fwgo');
  if (!f) { m.textContent = 'Choose a .bin file first.'; return; }
  if (/factory/i.test(f.name)) { m.textContent = 'That is the factory file. Use the catfinder-X.Y.Z.bin file without "factory".'; return; }
  stopAll();
  let sent = false;
  const end = text => { b.disabled = false; p.hidden = true; m.textContent = text; };
  b.disabled = true; p.hidden = false; p.value = 0; m.textContent = 'Uploading… 0%';
  const x = new XMLHttpRequest();
  x.open('POST', '/update');
  x.setRequestHeader('Content-Type', 'application/octet-stream');
  x.upload.onprogress = e => {
    if (!e.lengthComputable) return;
    const pc = Math.round(100 * e.loaded / e.total);
    p.value = pc; m.textContent = `Uploading… ${pc}%`;
  };
  x.upload.onload = () => { sent = true; m.textContent = 'Installing…'; };
  x.onload = () => {
    if (x.status === 200) { end('Installed. The robot restarts; this page reconnects in about 20 seconds.'); upd = 1; }
    else end(x.responseText || `Update failed (HTTP ${x.status}).`);
  };
  x.onerror = x.onabort = () => {
    if (sent) { end('The connection closed after the upload. The robot may be restarting; this page reconnects when it is back.'); upd = 1; }
    else end('Upload failed. Check the connection and try again.');
  };
  x.send(f);
};

// --- drive ---------------------------------------------------------------
// The firmware stops the servos if no /drive request arrives for 500 ms, so the
// current command is resent every 100 ms while a control is held.
const pad = $('pad'), knob = $('knob');
const maxEl = $('max'), maxv = $('maxv');
const drv = $('drv');
let padVec = null;           // {x, y} in -1..1 while the pad is held, y up
const keys = new Set();
let sentL = 0, sentR = 0, inFlight = false;

const showMax = () => { maxv.textContent = maxEl.value; };
maxEl.oninput = showMax; showMax();

function padFromEvent(e) {
  const b = pad.getBoundingClientRect(), rad = b.width / 2;
  let x = (e.clientX - b.left - rad) / rad, y = -(e.clientY - b.top - rad) / rad;
  const m = Math.hypot(x, y);
  if (m > 1) { x /= m; y /= m; }
  padVec = { x, y };
  knob.style.left = (50 + x * 50) + '%';
  knob.style.top = (50 - y * 50) + '%';
}
function padRelease() {
  padVec = null;
  pad.classList.remove('active');
  knob.style.left = knob.style.top = '50%';
}
pad.onpointerdown = e => {
  if (e.button > 0) return;
  pad.setPointerCapture(e.pointerId); pad.classList.add('active'); padFromEvent(e);
};
pad.onpointermove = e => { if (padVec) padFromEvent(e); };
pad.onpointerup = pad.onpointercancel = padRelease;
pad.oncontextmenu = e => e.preventDefault();

const KEYMAP = { w:'f', arrowup:'f', s:'b', arrowdown:'b', a:'l', arrowleft:'l', d:'r', arrowright:'r' };
const typing = t => t.tagName === 'TEXTAREA' || t.tagName === 'SELECT' || (t.tagName === 'INPUT' && t.type !== 'range');
addEventListener('keydown', e => {
  if (typing(e.target) || e.ctrlKey || e.metaKey || e.altKey) return;
  const k = KEYMAP[(e.key || '').toLowerCase()];
  if (k) { keys.add(k); e.preventDefault(); }
  if (e.key === ' ') { stopAll(); e.preventDefault(); }
});
addEventListener('keyup', e => {
  const k = KEYMAP[(e.key || '').toLowerCase()];
  if (k) keys.delete(k);
  if (e.key === ' ' && !typing(e.target)) e.preventDefault();  // no button click on release
});
addEventListener('blur', () => stopAll());
document.addEventListener('visibilitychange', () => { if (document.hidden) stopAll(); });

function stopAll() { keys.clear(); padRelease(); endTest(); endCal(); send(0, 0); }
$('stop').onclick = stopAll;

// Arcade mix: forward/back on y, turning on x; pure x spins on the spot.
function target() {
  let x = 0, y = 0;
  if (padVec) ({ x, y } = padVec);
  else {
    y = (keys.has('f') ? 1 : 0) - (keys.has('b') ? 1 : 0);
    x = (keys.has('r') ? 1 : 0) - (keys.has('l') ? 1 : 0);
    if (x && y) x *= 0.5;  // gentle curve instead of a pivot while driving
  }
  let l = y + x, r = y - x;
  const m = Math.max(1, Math.abs(l), Math.abs(r));
  const k = maxEl.value / m;
  return [Math.round(l * k), Math.round(r * k)];
}

async function send(l, r) {
  inFlight = true;
  try {
    await fetch(`/drive?l=${l}&r=${r}`, { cache: 'no-store' });
    sentL = l; sentR = r;
  } catch (e) {}
  inFlight = false;
  drv.textContent = `L ${l} · R ${r}`;
}

setInterval(() => {
  if (inFlight) return;
  const [l, r] = target();
  if (l || r) { endTest(); endCal(); }  // driving ends a servo test or calibration run
  if (calRun) { const v = CAL_LEVELS[calIdx]; send(v, v); return; }
  if (test && cfg) {
    inFlight = true;
    fetch(`/servo_test?s=${test.servo}&us=${testUs()}`, { cache: 'no-store' })
      .catch(() => {}).finally(() => { inFlight = false; });
    return;
  }
  if (l || r || sentL || sentR) send(l, r);
}, 100);

loadCfg(); poll();
setInterval(poll, 1000);
setInterval(pollLog, 3000);
</script>
</body>
</html>
)HTML";

static const char SETUP_HTML[] = R"HTML(<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>CatFinder setup</title>
<style>
:root{color-scheme:light dark;--bg:#f2f3f5;--card:#fff;--fg:#1b1d21;--dim:#6a7079;--line:#d8dbe0;--accent:#2563eb;--onacc:#fff;--bad:#dc2626}
@media(prefers-color-scheme:dark){:root{--bg:#101215;--card:#1a1d22;--fg:#e8e9ec;--dim:#9aa1ab;--line:#2f333a;--accent:#6d9bff;--onacc:#0b1220;--bad:#f0605d}}
*{box-sizing:border-box}
[hidden]{display:none!important}
body{margin:0;background:var(--bg);color:var(--fg);font:17px/1.45 system-ui,-apple-system,"Segoe UI",Roboto,Arial,sans-serif}
main{max-width:480px;margin:0 auto;padding:20px 16px 40px}
h1{font-size:22px;margin:4px 0 16px}
button,input{font:inherit}
.bar{display:flex;justify-content:space-between;align-items:center;margin-bottom:6px;color:var(--dim)}
.link{background:none;border:0;color:var(--accent);padding:8px 0;cursor:pointer}
#list{background:var(--card);border:1px solid var(--line);border-radius:12px;overflow:hidden}
.net{display:flex;align-items:center;gap:10px;width:100%;min-height:52px;padding:8px 14px;background:none;border:0;border-bottom:1px solid var(--line);color:var(--fg);text-align:left;cursor:pointer}
.net:last-child{border-bottom:0}
.net.sel{box-shadow:inset 4px 0 0 var(--accent);font-weight:600}
.nm{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.lock{width:16px;height:16px;color:var(--dim)}
.bars{display:flex;align-items:flex-end;gap:2px;height:16px}
.bars i{width:4px;background:var(--line);border-radius:1px}
.bars i.on{background:var(--fg)}
label{display:block;margin-top:16px;color:var(--dim);font-size:15px}
input{width:100%;margin-top:4px;padding:12px;border:1px solid var(--line);border-radius:10px;background:var(--card);color:var(--fg)}
.pw{display:flex;gap:8px}
.pw button{margin-top:4px;padding:0 14px;border:1px solid var(--line);border-radius:10px;background:var(--card);color:var(--fg);cursor:pointer}
.primary{width:100%;margin-top:20px;padding:14px;border:0;border-radius:10px;background:var(--accent);color:var(--onacc);font-weight:600;cursor:pointer}
button:disabled{opacity:.5}
#err{color:var(--bad);min-height:1.4em;margin:10px 0 0}
</style>
</head>
<body>
<main>
  <h1>Connect CatFinder to your WiFi</h1>
<form id="f" autocomplete="off" autocapitalize="none" spellcheck="false">
<div class="bar"><span id="state">Searching…</span><button type="button" class="link" id="again">Search again</button></div>
<div id="list"></div>
<label id="ssidbox" hidden>Network name<input id="ssid"></label>
<label id="pwbox" hidden>Password
<div class="pw"><input id="pass" type="password"><button type="button" id="show">Show</button></div></label>
<p id="err" role="alert"></p>
<button class="primary" id="go" disabled>Connect</button>
</form>
<div id="done" hidden>
<p>Saved. CatFinder now restarts and joins <b id="ssidout"></b>. Switch your phone back to your normal WiFi, then open the CatFinder app or http://catfinder.local</p>
<p>If CatFinder cannot join that network, the CatFinder hotspot comes back within a minute so you can try again.</p>
</div>
</main>
<script>
const $ = id => document.getElementById(id);
const say = t => { $('state').textContent = t; };
const err = t => { $('err').textContent = t; };
const esc = s => String(s).replace(/[&<>"']/g, c => '&#' + c.charCodeAt(0) + ';');
const LOCK = '<svg class="lock" viewBox="0 0 16 16" fill="currentColor"><path d="M4 7V5a4 4 0 0 1 8 0v2h1v8H3V7zm2 0h4V5a2 2 0 0 0-4 0z"/></svg>';
let nets = [], sel = null, other = false;
const level = r => r >= -55 ? 4 : r >= -65 ? 3 : r >= -75 ? 2 : 1;
const ssidNow = () => other ? $('ssid').value : sel ? sel.ssid : '';
function sync() {
 $('ssidbox').hidden = !other;
 $('pwbox').hidden = !(other || (sel && sel.secure));
 $('go').disabled = !ssidNow();
}
function renderList() {
 $('list').innerHTML = nets.map((n, i) => {
  const lv = level(n.rssi);
  return `<button type="button" class="net${!other && sel === n ? ' sel' : ''}" data-i="${i}">` +
   `<span class="nm">${esc(n.ssid)}</span>${n.secure ? LOCK : ''}<span class="bars">` +
   [1, 2, 3, 4].map(k => `<i style="height:${k * 4 + 4}px"${k <= lv ? ' class="on"' : ''}></i>`).join('') +
   '</span></button>';
 }).join('') + `<button type="button" class="net${other ? ' sel' : ''}" data-i="-1">Other network…</button>`;
}
$('list').onclick = e => {
 const b = e.target.closest('button');
 if (!b) return;
 other = +b.dataset.i < 0;
 sel = other ? null : nets[+b.dataset.i];
 renderList(); sync();
 const f = other ? 'ssid' : sel.secure ? 'pass' : '';
 if (f) $(f).focus();
};
async function scan() {
 say('Searching…');
 $('again').disabled = true;
 try {
  const r = await fetch('/wifi/scan', { cache: 'no-store' });
  if (!r.ok) throw new Error(r.status);
  const seen = new Set();
  nets = (await r.json()).filter(n => n.ssid && !seen.has(n.ssid) && seen.add(n.ssid));
  say(nets.length ? 'Choose your network' : 'No networks found');
 } catch (e) {
  nets = [];
  say('Search failed');
 }
 sel = sel && nets.find(n => n.ssid === sel.ssid) || null;
 $('again').disabled = false;
 renderList(); sync();
}
$('again').onclick = scan;
$('ssid').oninput = sync;
$('show').onclick = () => {
 const p = $('pass'), show = p.type === 'password';
 p.type = show ? 'text' : 'password';
 $('show').textContent = show ? 'Hide' : 'Show';
};
$('f').onsubmit = async e => {
 e.preventDefault();
 const ssid = ssidNow(), pass = $('pass').value, go = $('go');
 if (!ssid) return;
 if (sel && sel.secure && !pass) { err('Enter the password.'); return; }
 err('');
 go.disabled = true; go.textContent = 'Saving…';
 try {
  const r = await fetch('/wifi', { method: 'POST', body: new URLSearchParams({ ssid, pass }) });
  const t = await r.text();
  if (!r.ok || t.trim() !== 'ok') throw new Error(t);
  $('f').hidden = true;
  $('done').hidden = false;
  $('ssidout').textContent = ssid;
 } catch (x) {
  err('Could not save. Stay connected to the CatFinder hotspot and try again.');
  go.disabled = false; go.textContent = 'Connect';
 }
};
renderList();
scan();
</script>
</body>
</html>
)HTML";

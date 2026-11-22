(function(){
"use strict";
var GB = window.GrokBall;
var ballEl = document.getElementById("ball");
var readout = document.getElementById("readout");
var logEl = document.getElementById("log");

var S = {
  id: "02",
  engine: null,
  override: false,
  animOff: false,
  animOnly: null,
  patch: { body: {}, eyes: {} },
  backdrop: "dark"
};

function log(m){
  var t = new Date();
  var p = function(n){ return (n<10?"0":"")+n; };
  var line = "[" + p(t.getHours()) + ":" + p(t.getMinutes()) + ":" + p(t.getSeconds()) + "] " + m;
  logEl.innerHTML = line + "<br>" + logEl.innerHTML;
}

function metaOf(id){
  var all = GB.EMOTIONS;
  for (var i=0;i<all.length;i++) if (all[i].id === id) return all[i];
  return { id:id, name:id, group:"custom", desc:"" };
}

function defOf(id){ return GB.config.get(id); }

function clone(o){ return JSON.parse(JSON.stringify(o)); }

var seedRaw = {};

function buildRaw(){
  var base = seedRaw[S.id];
  if (!base) return null;
  var raw = clone(base);
  raw.id = S.id; // Keep the state's safe silhouette when previewing parameter overrides.
  if (S.override || S.animOff || S.animOnly != null) {
    raw.name = "调试中 · " + base.name;
    raw.group = "custom";
  }
  if (S.animOff) raw.anims = [];
  else if (S.animOnly != null) raw.anims = [clone((base.anims || [])[S.animOnly])];
  if (S.override) {
    raw.body = raw.body || {};
    raw.eyes = raw.eyes || {};
    raw.eyes.both = raw.eyes.both || {};
    var k;
    for (k in S.patch.body) raw.body[k] = S.patch.body[k];
    for (k in S.patch.eyes) raw.eyes.both[k] = S.patch.eyes[k];
  }
  return raw;
}

function applyFace(){
  var raw = buildRaw();
  if (!raw) { log("没有找到表情 " + S.id); return; }
  // Register the immutable seed again when overrides or animation filters are cleared.
  var r = GB.config.register(raw);
  if (!r.ok) { log("注册失败：" + r.errors.join("；")); return; }
  S.engine.setEmotion(S.id);
  renderReadout();
  renderRaw();
}

function renderReadout(){
  var d = defOf(S.id);
  var parts = [];
  parts.push("<b>" + (d ? d.name : S.id) + "</b> · ID " + S.id + " · " + (d ? d.group : ""));
  var bits = [];
  if (S.override) bits.push("参数覆盖");
  if (S.animOff) bits.push("动画全停");
  else if (S.animOnly != null) bits.push("只播 " + (d && d.anims[S.animOnly] ? d.anims[S.animOnly].type : "?"));
  if (!bits.length) bits.push("原始配置");
  parts.push(bits.join(" · "));
  var fx = [];
  if (d && d.base.body.zzz > 0) fx.push("zzz");
  if (d && d.base.body.orbit > 0) fx.push("orbit");
  if (d && d.base.body.ribbons > 0) fx.push("ribbons");
  if (d && d.base.body.confetti > 0) fx.push("confetti");
  if (d && d.sequence) fx.push("sequence " + d.sequence.frames.length + " 帧");
  parts.push("动画 " + (d ? d.anims.length : 0) + " 条" + (fx.length ? " · 特效 " + fx.join("/") : ""));
  readout.innerHTML = parts.join("<br>");
}

function renderRaw(){
  var d = defOf(S.id);
  var box = document.getElementById("rawBox");
  if (!d) { box.textContent = ""; return; }
  var raw = (S.override || S.animOff || S.animOnly != null) ? buildRaw() : d.raw;
  box.textContent = JSON.stringify(raw, null, 2);
}

/* ---------- 表情页 ---------- */
function renderFaces(){
  var grid = document.getElementById("faceGrid");
  var groups = GB.GROUPS;
  var all = GB.EMOTIONS;
  var html = [];
  groups.forEach(function(g){
    var items = all.filter(function(e){ return e.group === g.key; });
    if (!items.length) return;
    html.push("<h2>" + g.name + " · " + items.length + "</h2>");
    html.push("<div class=\"grid\">");
    items.forEach(function(e){
      html.push("<button class=\"face" + (e.id === S.id ? " on" : "") + "\" data-id=\"" + e.id + "\">" +
        "<div class=\"thumb\" data-thumb=\"" + e.id + "\"></div><span>" + e.id + " " + e.name + "</span></button>");
    });
    html.push("</div>");
  });
  grid.innerHTML = html.join("");
  var thumbs = grid.querySelectorAll("[data-thumb]");
  for (var i=0;i<thumbs.length;i++){
    GB.create(thumbs[i], { emotion: thumbs[i].getAttribute("data-thumb"), autostart: false, eyeScale: 1.35 });
  }
  var cards = grid.querySelectorAll(".face");
  for (var j=0;j<cards.length;j++){
    cards[j].addEventListener("click", function(){
      S.id = this.getAttribute("data-id");
      S.animOnly = null;
      S.patch = { body:{}, eyes:{} };
      S.override = false;
      document.getElementById("swOv").classList.remove("on");
      var on = grid.querySelectorAll(".face.on");
      for (var k=0;k<on.length;k++) on[k].classList.remove("on");
      this.classList.add("on");
      applyFace();
      renderAnims();
      renderParams();
      log("切到 " + S.id + " " + metaOf(S.id).name);
    });
  }
}

/* ---------- 动画页 ---------- */
function renderAnims(){
  var d = defOf(S.id);
  var box = document.getElementById("animList");
  if (!d) { box.innerHTML = ""; return; }
  var html = [];
  if (!d.anims.length) html.push("<div class=\"row\"><div class=\"rowSub\">这个表情没有 ANIM_TYPES 动画条目</div></div>");
  d.anims.forEach(function(a, i){
    var keys = [];
    for (var k in a) if (k !== "type") keys.push(k + "=" + a[k]);
    html.push("<div class=\"row\"><div class=\"rowTitle\">" + (i+1) + ". " + a.type + " → " + a.target + "." + a.prop + "</div>" +
      "<div class=\"rowSub\">" + keys.join(" · ") + "</div>" +
      "<div class=\"btns\"><button class=\"act" + (S.animOnly === i ? " primary" : "") + "\" data-anim=\"" + i + "\">只看这一条</button></div></div>");
  });
  html.push("<div class=\"row\"><div class=\"rowTitle\">恢复全部动画</div><div class=\"rowSub\">清掉单条筛选，回到原始配置</div>" +
    "<div class=\"btns\"><button class=\"act\" id=\"btnAllAnim\">恢复</button></div></div>");
  box.innerHTML = html.join("");
  var btns = box.querySelectorAll("[data-anim]");
  for (var i=0;i<btns.length;i++){
    btns[i].addEventListener("click", function(){
      S.animOnly = parseInt(this.getAttribute("data-anim"), 10);
      S.animOff = false;
      document.getElementById("swNoAnim").classList.remove("on");
      applyFace();
      renderAnims();
      log("只播第 " + (S.animOnly+1) + " 条动画");
    });
  }
  var all = document.getElementById("btnAllAnim");
  if (all) all.addEventListener("click", function(){
    S.animOnly = null; S.animOff = false;
    document.getElementById("swNoAnim").classList.remove("on");
    applyFace(); renderAnims(); log("恢复全部动画");
  });
}

/* ---------- 参数页 ---------- */
var BODY_RANGES = {
  x:[-40,40,1], y:[-40,40,1], rotate:[-180,180,1],
  sketch:[0,1,0.02], zzz:[0,1,0.02], orbit:[0,1,0.02],
  ribbons:[0,1,1], confetti:[0,1,0.05]
};
var EYE_RANGES = {
  open:[0.02,1.3,0.02], lookX:[-24,24,0.5], lookY:[-15,15,0.5]
};

function slider(kind, key, min, max, step, cur){
  var val = (cur == null ? (kind === "body" ? 0 : 0) : cur);
  return "<div class=\"ctl\"><label>" + kind + "." + key + "</label>" +
    "<input type=\"range\" data-kind=\"" + kind + "\" data-key=\"" + key + "\" min=\"" + min + "\" max=\"" + max + "\" step=\"" + step + "\" value=\"" + val + "\">" +
    "<span class=\"val\" data-show=\"" + kind + "." + key + "\">" + val + "</span></div>";
}

function renderParams(){
  var d = defOf(S.id);
  var box = document.getElementById("paramBody");
  if (!d) { box.innerHTML = ""; return; }
  var b = d.base.body, e = d.base.left;
  var html = [];
  html.push("<h2>身体</h2><div class=\"row\">");
  for (var k in BODY_RANGES){
    var cur = (S.patch.body[k] != null ? S.patch.body[k] : b[k]);
    html.push(slider("body", k, BODY_RANGES[k][0], BODY_RANGES[k][1], BODY_RANGES[k][2], cur));
  }
  html.push("<div class=\"ctl\"><label>body.color</label><input type=\"color\" id=\"cBodyColor\" value=\"" + (b.color || "#F3F0EA") + "\"></div>");
  html.push("</div>");
  html.push("<h2>眼睛（左右同步）</h2><div class=\"row\">");
  for (var k2 in EYE_RANGES){
    var cur2 = (S.patch.eyes[k2] != null ? S.patch.eyes[k2] : e[k2]);
    html.push(slider("eyes", k2, EYE_RANGES[k2][0], EYE_RANGES[k2][1], EYE_RANGES[k2][2], cur2));
  }
  html.push("<div class=\"ctl\"><label>eyes.color</label><input type=\"color\" id=\"cEyeColor\" value=\"" + (e.color || "#1A1A1A") + "\"></div>");
  html.push("</div>");
  html.push("<div class=\"btns\" style=\"margin:10px 2px\"><button class=\"act warn\" id=\"btnResetParam\">参数复位</button></div>");
  box.innerHTML = html.join("");
  var inputs = box.querySelectorAll("input[type=range]");
  for (var i=0;i<inputs.length;i++){
    inputs[i].addEventListener("input", function(){
      var kind = this.getAttribute("data-kind"), key = this.getAttribute("data-key");
      var v = parseFloat(this.value);
      S.patch[kind][key] = v;
      var show = box.querySelector("[data-show=\"" + kind + "." + key + "\"]");
      if (show) show.textContent = v;
      if (!S.override) { S.override = true; document.getElementById("swOv").classList.add("on"); }
      debounceApply();
    });
  }
  var cb = document.getElementById("cBodyColor");
  if (cb) cb.addEventListener("change", function(){
    S.patch.body.color = this.value;
    if (!S.override) { S.override = true; document.getElementById("swOv").classList.add("on"); }
    debounceApply();
  });
  var ce = document.getElementById("cEyeColor");
  if (ce) ce.addEventListener("change", function(){
    S.patch.eyes.color = this.value;
    if (!S.override) { S.override = true; document.getElementById("swOv").classList.add("on"); }
    debounceApply();
  });
  var rp = document.getElementById("btnResetParam");
  if (rp) rp.addEventListener("click", function(){
    S.patch = { body:{}, eyes:{} }; S.override = false;
    document.getElementById("swOv").classList.remove("on");
    applyFace(); renderParams(); log("参数复位");
  });
}

var applyTimer = 0;
function debounceApply(){
  if (applyTimer) clearTimeout(applyTimer);
  applyTimer = setTimeout(function(){ applyTimer = 0; applyFace(); }, 90);
}

/* ---------- 动作页 ---------- */
function renderActs(){
  var box = document.getElementById("actBody");
  var html = [];
  html.push("<h2>一次性特效</h2><div class=\"row\"><div class=\"btns\">" +
    "<button class=\"act\" data-act=\"spin1\">自旋 1 圈</button>" +
    "<button class=\"act\" data-act=\"spin2\">自旋 2 圈</button>" +
    "<button class=\"act\" data-act=\"spinr\">反向自旋</button>" +
    "<button class=\"act\" data-act=\"burst\">撒花 20</button>" +
    "<button class=\"act\" data-act=\"bounce\">弹跳</button>" +
    "</div></div>");
  html.push("<h2>注视</h2><div class=\"row\"><div class=\"btns\">" +
    "<button class=\"act\" data-gaze=\"-1,0\">看左</button>" +
    "<button class=\"act\" data-gaze=\"1,0\">看右</button>" +
    "<button class=\"act\" data-gaze=\"0,-1\">看上</button>" +
    "<button class=\"act\" data-gaze=\"0,1\">看下</button>" +
    "<button class=\"act\" data-gaze=\"0,0\">回正</button>" +
    "<button class=\"act\" data-act=\"cleargaze\">松开注视</button>" +
    "</div><div class=\"rowSub\">屏幕上也跟随手指：按住小球区域拖动</div></div>");
  html.push("<h2>巡演</h2><div class=\"row\"><div class=\"btns\">" +
    "<button class=\"act primary\" data-act=\"tourall\">全部 32 个走一遍</button>" +
    "<button class=\"act\" data-act=\"touremotion\">情绪组走一遍</button>" +
    "<button class=\"act\" data-act=\"touragent\">代理组走一遍</button>" +
    "<button class=\"act warn\" data-act=\"stoptour\">停</button>" +
    "</div><div class=\"ctl\"><label>间隔 ms</label><input type=\"range\" id=\"tourMs\" min=\"400\" max=\"4000\" step=\"100\" value=\"1600\"><span class=\"val\" id=\"tourMsShow\">1600</span></div></div>");
  html.push("<h2>渲染开关</h2><div class=\"row\">" +
    "<div class=\"btns\">" +
    "<button class=\"act\" data-act=\"replay\">重播当前表情</button>" +
    "<button class=\"act\" data-act=\"static\">静态渲染一帧</button>" +
    "<button class=\"act\" data-act=\"stopanim\">停掉 rAF</button>" +
    "<button class=\"act\" data-act=\"startanim\">重新开 rAF</button>" +
    "<button class=\"act\" data-act=\"sketch\">草图风开</button>" +
    "<button class=\"act\" data-act=\"nosketch\">草图风关</button>" +
    "</div></div>");
  html.push("<h2>底色预览</h2><div class=\"row\"><div class=\"btns\">" +
    "<button class=\"act\" data-bg=\"paper\">纸面 #F4F0E8</button>" +
    "<button class=\"act\" data-bg=\"card\">卡片 #FBF8F1</button>" +
    "<button class=\"act\" data-bg=\"dark\">深色 #1A1917</button>" +
    "</div><div class=\"rowSub\">看球体本色在哪个底上还认得出边界</div></div>");
  html.push("<h2>AI 消息接口</h2><div class=\"row\"><div class=\"btns\">" +
    "<button class=\"act\" data-act=\"aimsg\">handleAIMessage 切到 32</button>" +
    "<button class=\"act warn\" data-act=\"aibad\">发一条非法消息</button>" +
    "</div><div class=\"rowSub\">验证 一条 {emotionId, tips} 消息 这条对接路径</div></div>");
  box.innerHTML = html.join("");
  var btns = box.querySelectorAll("button");
  for (var i=0;i<btns.length;i++) btns[i].addEventListener("click", onAct);
  var tm = document.getElementById("tourMs");
  tm.addEventListener("input", function(){ document.getElementById("tourMsShow").textContent = this.value; });
}

function idsOf(group){
  return GB.EMOTIONS.filter(function(e){ return e.group === group; }).map(function(e){ return e.id; });
}

function onAct(){
  var e = S.engine;
  var act = this.getAttribute("data-act");
  var gz = this.getAttribute("data-gaze");
  var bg = this.getAttribute("data-bg");
  var ms = parseInt((document.getElementById("tourMs")||{value:1600}).value, 10) || 1600;
  if (gz) {
    var p = gz.split(",");
    e.setGaze(parseFloat(p[0]), parseFloat(p[1]));
    log("注视 " + gz);
    return;
  }
  if (bg) {
    S.backdrop = bg;
    var c = { paper:"#F4F0E8", card:"#FBF8F1", dark:"#1A1917" }[bg];
    // 只换舞台底色：整页一起变深会让标题与说明也变成墨色压深色，字体直接消失。
    document.querySelector(".stageBox").style.background = c;
    log("舞台底色 " + c);
    return;
  }
  switch (act) {
    case "spin1": e.spin(1); log("自旋 1 圈"); break;
    case "spin2": e.spin(2); log("自旋 2 圈"); break;
    case "spinr": e.spin(1, -1); log("反向自旋"); break;
    case "burst": e.burst(20); log("撒花"); break;
    case "bounce": e.bounce(); log("弹跳"); break;
    case "cleargaze": e.clearGaze(); log("松开注视"); break;
    case "tourall": e.startTour(GB.EMOTIONS.map(function(x){return x.id;}), ms); log("全部巡演 间隔 " + ms + "ms"); break;
    case "touremotion": e.startTour(idsOf("emotion"), ms); log("情绪组巡演"); break;
    case "touragent": e.startTour(idsOf("agent"), ms); log("代理组巡演"); break;
    case "stoptour": e.stopTour(); log("停止巡演"); break;
    case "replay": e.replay(); log("重播"); break;
    case "static": e.setActive(false); e.renderStatic(); log("静态一帧"); break;
    case "stopanim": e.setActive(false); log("rAF 已停"); break;
    case "startanim": e.setActive(true); log("rAF 已开"); break;
    case "sketch": e.setStyle("sketch"); log("草图风"); break;
    case "nosketch": e.setStyle("plain"); log("关闭草图风"); break;
    case "aimsg": e.handleAIMessage({ emotionId:"32", tips:"实验室：收到任务" }); log("handleAIMessage 32"); break;
    case "aibad": e.handleAIMessage("这不是 JSON"); log("发了非法消息，应回退到待机"); break;
  }
}

/* ---------- 顶栏与开关 ---------- */
function bindTabs(){
  var btns = document.querySelectorAll("#tabs button");
  for (var i=0;i<btns.length;i++){
    btns[i].addEventListener("click", function(){
      var t = this.getAttribute("data-tab");
      var all = document.querySelectorAll("#tabs button");
      for (var k=0;k<all.length;k++) all[k].classList.remove("on");
      this.classList.add("on");
      var secs = document.querySelectorAll("section[data-tab]");
      for (var m=0;m<secs.length;m++) secs[m].classList.toggle("on", secs[m].getAttribute("data-tab") === t);
    });
  }
}

function bindSwitches(){
  document.getElementById("swNoAnim").addEventListener("click", function(){
    S.animOff = !S.animOff; S.animOnly = null;
    this.classList.toggle("on", S.animOff);
    applyFace(); renderAnims();
    log(S.animOff ? "动画全停" : "动画恢复");
  });
  document.getElementById("swOv").addEventListener("click", function(){
    S.override = !S.override;
    this.classList.toggle("on", S.override);
    applyFace();
    log(S.override ? "参数覆盖开" : "参数覆盖关");
  });
  document.getElementById("btnExport").addEventListener("click", function(){
    document.getElementById("expBox").textContent = GB.config.exportConfig();
    log("导出了全部表情配置");
  });
}

function bindGaze(){
  var stage = document.getElementById("ball");
  function move(ev){
    var r = stage.getBoundingClientRect();
    if (!r.width) return;
    var cx = r.left + r.width/2, cy = r.top + r.height/2;
    var nx = (ev.clientX - cx) / (r.width/2);
    var ny = (ev.clientY - cy) / (r.height/2);
    nx = Math.max(-1, Math.min(1, nx)); ny = Math.max(-1, Math.min(1, ny));
    S.engine.setGaze(nx, ny);
  }
  stage.addEventListener("pointermove", move, { passive:true });
  stage.addEventListener("pointerleave", function(){ S.engine.clearGaze(); });
}

function boot(){
  GB.EMOTIONS.forEach(function(meta){ seedRaw[meta.id] = clone(defOf(meta.id).raw); });
  S.engine = GB.create(ballEl, {
    emotion: "02",
    color: "#F3F0E9",
    eyeColor: "#111516",
    idle: false
  });
  S.engine.on("change", function(p){
    if (!p.auto) return;
    // 巡演是引擎自己换的表情，实验室的选中态、读数、参数页都得跟着走，
    // 否则屏幕上换了脸、下面的字还写着上一个 —— 这正是"看到的和读到的对不上"。
    S.id = p.id;
    S.animOnly = null;
    S.override = false;
    S.patch = { body:{}, eyes:{} };
    var sw = document.getElementById("swOv");
    if (sw) sw.classList.remove("on");
    var on = document.querySelectorAll(".face.on");
    for (var k=0;k<on.length;k++) on[k].classList.remove("on");
    var cur = document.querySelector('.face[data-id="' + p.id + '"]');
    if (cur) cur.classList.add("on");
    renderReadout();
    renderRaw();
    renderAnims();
    renderParams();
    log("巡演切到 " + p.id + " " + (p.def ? p.def.name : ""));
  });
  bindTabs();
  bindSwitches();
  bindGaze();
  renderFaces();
  renderAnims();
  renderParams();
  renderActs();
  renderReadout();
  renderRaw();
  log("引擎 v" + GB.version + " 就绪，共 " + GB.EMOTIONS.length + " 个表情");
  window.LAB = {
    set: function(id){ S.id = id; S.animOnly = null; S.override = false; S.patch = { body:{}, eyes:{} }; applyFace(); renderAnims(); renderParams(); },
    state: function(){ return { id: S.id, override: S.override, animOff: S.animOff, animOnly: S.animOnly }; },
    export: function(){ return GB.config.exportConfig(); }
  };
}

if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot);
else boot();
})();
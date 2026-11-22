/*! Grok Ball standalone engine | MIT License */
/*
 * Grok Ball standalone engine.
 * Bundled in dependency order: ring geometry, emotion data, renderer, driver.
 */
/* ============================================================
 * rings.js —— 几何数据层（纯数据）
 *   EXPRESSIONS：25 组安全平行眼形（12 状态轮用，不保留旧内聚眼环）
 *   SHAPES：身体形状环（blob 圆胖 / wedge 三角 / gem 菱形）+ 脸部拟合参数
 *   坐标系：viewBox -15 -15 259 259，头部中心 114.2705
 * ============================================================ */
// BEGIN GENERATED SAFE FACE POLICY
// MIT Grok Ball animation APIs retained; application-owned parallel-eye geometry.
window.EB_SAFE_FACE = (function () {
  var specs = [
  [0.15, 0.014, 0.035],
  [0.095, 0.065, 0.015],
  [0.09, 0.125, 0.0],
  [0.112, 0.16, -0.025],
  [0.13, 0.07, 0.0],
  [0.102, 0.095, -0.035],
  [0.115, 0.082, 0.02],
  [0.145, 0.045, -0.015],
  [0.11, 0.145, 0.025],
  [0.1, 0.135, -0.012],
  [0.128, 0.155, 0.0],
  [0.085, 0.165, -0.02]
  ];
  var ids = {"00":0,"01":1,"02":2,"03":3,"16":4,"30":5,"32":6,"33":7,"34":8,"35":9,"36":10,"40":11};
  function ring(center, spec) {
    var radius = Math.min(spec[0], spec[1]) / 2;
    var horizontal = spec[0] / 2 - radius, vertical = spec[1] / 2 - radius;
    var result = [];
    for (var i = 0; i < 48; i++) {
      var angle = 2 * Math.PI * i / 48, cx = Math.cos(angle), sy = Math.sin(angle);
      result.push([114.2705 + 228.54 * (center + radius * cx + (cx >= 0 ? horizontal : -horizontal)),
                   114.2705 + 228.54 * (spec[2] + radius * sy + (sy >= 0 ? vertical : -vertical))]);
    }
    return result;
  }
  var pairs = specs.map(function (s) { return [ring(-0.145, s), ring(0.145, s)]; });
  function pairOf(index) { return pairs[index % pairs.length]; }
  return {
    indexOf: function (id) { return Object.prototype.hasOwnProperty.call(ids, id) ? ids[id] : 2; },
    pairOf: pairOf,
    expressions: Array.from({length: 25}, function (_, i) { return pairOf(i); }),
    // This final pose gate covers seed/default, register/import, AI messages, tours and sequences.
    constrain: function (pose) {
      var a = pose.left, b = pose.right;
      ['x', 'y', 'lookX', 'lookY', 'scaleX', 'scaleY', 'open'].forEach(function (key) {
        var fallback = key === 'scaleX' || key === 'scaleY' || key === 'open' ? 1 : 0;
        var av = Number.isFinite(a[key]) ? a[key] : fallback;
        var bv = Number.isFinite(b[key]) ? b[key] : fallback;
        var shared = (av + bv) / 2;
        if (key === 'x' || key === 'y') shared = 0;
        if (key === 'lookX' || key === 'lookY') shared = Math.max(-24, Math.min(24, shared));
        if (key === 'scaleX' || key === 'scaleY') shared = 1;
        if (key === 'open') shared = Math.max(0.02, Math.min(1.3, shared));
        a[key] = b[key] = shared;
      });
      a.rotate = b.rotate = 0;
      // Rebuild even externally supplied rings: bounds may vary, old oblique paths cannot survive.
      if (a.ring || b.ring) {
        var source = a.ring || b.ring;
        var xs = source.map(function (p) { return p[0]; }), ys = source.map(function (p) { return p[1]; });
        var minX = Math.min.apply(null, xs), maxX = Math.max.apply(null, xs);
        var minY = Math.min.apply(null, ys), maxY = Math.max.apply(null, ys);
        var width = Math.max(0.05, Math.min(0.16, (maxX - minX) / 228.54));
        var height = Math.max(0.014, Math.min(0.18, (maxY - minY) / 228.54));
        var y = Math.max(-0.05, Math.min(0.05, ((minY + maxY) / 2 - 114.2705) / 228.54));
        var spec = [Number.isFinite(width) ? width : 0.09, Number.isFinite(height) ? height : 0.125,
                    Number.isFinite(y) ? y : 0];
        a.ring = ring(-0.145, spec); b.ring = ring(0.145, spec);
      }
      pose.body.scale = 1;
      pose.body.breathe = 0;
      return pose;
    }
  };
})();
// END GENERATED SAFE FACE POLICY
window.EB_RINGS = {
  HEAD_C: 114.2705,
  EYE_HALF: 21,
  STAR_GOLD: '#f4c34e',
  EXPRESSIONS: window.EB_SAFE_FACE.expressions,
  SHAPES: {"blob":{"ring":[[228.54,114.27],[228.29,121.74],[227.55,129.18],[226.32,136.56],[224.62,143.84],[222.45,150.99],[219.82,157.99],[216.73,164.8],[213.2,171.39],[209.25,177.73],[204.89,183.81],[200.15,189.59],[195.04,195.04],[189.59,200.15],[183.81,204.89],[177.73,209.24],[171.38,213.19],[164.78,216.7],[157.97,219.78],[150.98,222.4],[143.82,224.56],[136.54,226.25],[129.17,227.46],[121.74,228.19],[114.27,228.44],[106.8,228.2],[99.37,227.48],[91.99,226.27],[84.71,224.58],[77.56,222.42],[70.56,219.79],[63.75,216.71],[57.16,213.18],[50.81,209.24],[44.74,204.89],[38.96,200.15],[33.5,195.04],[28.39,189.59],[23.65,183.81],[19.29,177.73],[15.35,171.38],[11.82,164.79],[8.73,157.99],[6.09,150.99],[3.92,143.84],[2.21,136.56],[0.99,129.18],[0.25,121.74],[0,114.27],[0.25,106.8],[0.98,99.36],[2.2,91.98],[3.9,84.7],[6.06,77.54],[8.69,70.54],[11.78,63.73],[15.3,57.13],[19.25,50.78],[23.61,44.7],[28.35,38.92],[33.46,33.46],[38.91,28.34],[44.69,23.59],[50.77,19.24],[57.12,15.29],[63.72,11.77],[70.54,8.69],[77.54,6.07],[84.7,3.9],[91.98,2.21],[99.36,1],[106.8,0.26],[114.27,0.01],[121.74,0.25],[129.19,0.98],[136.57,2.19],[143.85,3.88],[151.01,6.04],[158.01,8.68],[164.82,11.76],[171.42,15.29],[177.77,19.24],[183.84,23.6],[189.62,28.35],[195.08,33.46],[200.2,38.92],[204.94,44.7],[209.29,50.78],[213.23,57.13],[216.76,63.73],[219.84,70.54],[222.48,77.54],[224.65,84.7],[226.34,91.98],[227.56,99.36],[228.29,106.8]],"face":{"x":0,"y":0,"sx":1,"sy":1,"eye":1},"tiltScale":1},"wedge":{"ring":[[192.97,114.27],[196.06,119.63],[199.44,125.48],[203.18,131.95],[207.37,139.22],[212.15,147.49],[217.65,157.09],[223,167.89],[227.01,179.36],[228.48,190.58],[226.47,200.37],[221.01,207.88],[213.01,213.01],[203.59,216.12],[193.71,217.79],[183.88,218.45],[174.46,218.53],[165.69,218.53],[157.46,218.53],[149.66,218.53],[142.21,218.53],[135.01,218.53],[128,218.53],[121.1,218.53],[114.27,218.53],[107.44,218.53],[100.54,218.53],[93.53,218.53],[86.33,218.53],[78.88,218.53],[71.08,218.53],[62.86,218.53],[54.08,218.53],[44.66,218.45],[34.83,217.79],[24.95,216.12],[15.53,213.01],[7.53,207.88],[2.07,200.36],[0.06,190.58],[1.54,179.36],[5.54,167.89],[10.9,157.09],[16.39,147.5],[21.17,139.22],[25.36,131.95],[29.1,125.48],[32.48,119.63],[35.57,114.27],[38.44,109.3],[41.13,104.64],[43.68,100.23],[46.12,96.01],[48.47,91.93],[50.76,87.96],[53.01,84.06],[55.25,80.19],[57.48,76.32],[59.73,72.42],[62.02,68.45],[64.38,64.38],[66.81,60.16],[69.36,55.74],[72.05,51.08],[74.92,46.11],[78.02,40.77],[81.5,35.15],[85.47,29.43],[90.02,23.78],[95.22,18.51],[101.08,14.09],[107.51,11.1],[114.27,10.01],[121.03,11.1],[127.46,14.09],[133.32,18.51],[138.52,23.78],[143.07,29.43],[147.04,35.15],[150.52,40.77],[153.62,46.11],[156.49,51.08],[159.18,55.74],[161.73,60.16],[164.16,64.38],[166.52,68.45],[168.81,72.42],[171.06,76.32],[173.29,80.19],[175.53,84.06],[177.78,87.96],[180.07,91.93],[182.42,96.01],[184.86,100.23],[187.41,104.64],[190.1,109.3]],"face":{"x":0,"y":24,"sx":0.7,"sy":0.7,"eye":0.79},"tiltScale":0.22},"gem":{"ring":[[227.48,114.27],[226.24,121.61],[224.05,128.72],[221.32,135.56],[218.2,142.12],[214.8,148.39],[211.15,154.4],[207.33,160.16],[203.34,165.7],[199.19,171.01],[194.92,176.15],[190.48,181.1],[185.91,185.91],[181.16,190.55],[176.26,195.06],[171.17,199.42],[165.87,203.65],[160.36,207.73],[154.61,211.65],[148.6,215.39],[142.31,218.9],[135.72,222.12],[128.84,224.94],[121.67,227.2],[114.27,228.49],[106.87,227.2],[99.7,224.94],[92.82,222.12],[86.24,218.9],[79.95,215.39],[73.94,211.65],[68.18,207.73],[62.67,203.65],[57.37,199.43],[52.28,195.06],[47.38,190.55],[42.64,185.91],[38.06,181.1],[33.62,176.15],[29.35,171.01],[25.2,165.69],[21.22,160.16],[17.39,154.4],[13.74,148.4],[10.34,142.12],[7.23,135.56],[4.49,128.72],[2.3,121.61],[1.06,114.27],[2.3,106.93],[4.49,99.82],[7.23,92.98],[10.34,86.42],[13.74,80.15],[17.39,74.14],[21.22,68.38],[25.2,62.85],[29.35,57.53],[33.63,52.39],[38.06,47.44],[42.64,42.64],[47.38,37.99],[52.28,33.48],[57.37,29.12],[62.67,24.89],[68.18,20.81],[73.94,16.89],[79.94,13.15],[86.24,9.64],[92.82,6.42],[99.7,3.6],[106.87,1.34],[114.27,0.05],[121.67,1.34],[128.84,3.6],[135.72,6.42],[142.31,9.64],[148.6,13.15],[154.61,16.89],[160.36,20.81],[165.87,24.9],[171.17,29.12],[176.26,33.48],[181.16,37.99],[185.91,42.64],[190.48,47.44],[194.91,52.39],[199.19,57.53],[203.34,62.85],[207.33,68.38],[211.15,74.14],[214.8,80.15],[218.2,86.42],[221.32,92.98],[224.05,99.82],[226.24,106.93]],"face":{"x":0,"y":0,"sx":0.89,"sy":0.89,"eye":0.99},"tiltScale":1}}
};


/* ============================================================
 * emotions.js —— 数据层（纯数据，不含任何 DOM / 逻辑代码）
 *
 * ID 分段规则（十位 = 分组前缀，组间空号为扩展预留位，编号即对外契约不可重排）：
 *   00-09 生命周期 · 10-29 情绪反应 · 30-49 代理工作状态 · 50+ 自定义
 *
 * 32 套表情的完整定义，配置字段：
 *   pool      眼环索引池（25 组轮廓眼环，逐点弹簧插值切换）
 *   poolMs    池内轮换间隔 [min, max] ms
 *   poolSpeed 形变弹簧频率（检索 / 兴奋类 10，默认 6）
 *   blinkMs   眨眼间隔 [min, max] ms（null = 不眨眼）
 *   openness  常驻开合度（如睡眠 0.08、疲惫 0.55）
 *   antics    待机随机小动作（自旋 / 弹跳，9~18s 一次）
 *   ribbons / confetti  进入表情时的一次性事件：自旋甩彩带 / 撒花
 *   body.zzz   睡眠字母粒子（右上角循环漂浮）
 *   body.orbit 常驻水平环带（头顶彩带持续环绕）
 *   sequence  关键帧序列，settle: 'base' 回落基础姿态 / 'hold' 定格末帧
 *             / { next } 播完切换到下一个表情
 *   en        英文文案 { name, desc }（界面双语用，引擎不消费）
 *
 * 眼环池速查：
 *   0/8 平静 · 2/11/17/19 笑眼 · 3/21 圆睁 · 13/22/4 闭合困倦
 *   14/5/23 斜眼 · 7/16 怒目 · 15/9/20/12/18 扫读 · 10/1 聆听 · 24 羞怯
 * ============================================================ */

window.EMOTION_GROUPS = [
  { key: 'life',    name: '生命周期',     en: 'Lifecycle' },
  { key: 'emotion', name: '情绪反应',     en: 'Emotions' },
  { key: 'agent',   name: '代理工作状态', en: 'Agent States' },
  { key: 'custom',  name: '自定义',       en: 'Custom' }
];

window.EMOTION_SEED = [

  /* ==================== 1）生命周期（8 个） ==================== */

  {
    id: '00', name: '睡眠', group: 'life',
    desc: '闭眼成细线，右上角 zzz 缓缓飘起，头微垂，只剩缓慢呼吸',
    en: { name: 'Sleeping', desc: 'Eyes closed to thin lines, zzz drifting up at the top right, only a slow breath remains' },
    transition: 900,
    gaze: false,
    pool: [13, 22, 4], poolMs: [6000, 10000], blinkMs: null, openness: 0.08,
    body: { y: 4, rotate: -2, breathe: 0.018, color: '#EEEBE4', zzz: 1 },
    eyes: { both: { y: 4, lookY: 2 } },
    anims: [
      { target: 'eyes', prop: 'y', type: 'sine', amp: 1.2, period: 3600 }
    ]
  },
  {
    id: '01', name: '唤醒', group: 'life',
    desc: '从闭合眼环缓缓睁开，先揉眼似的眨两下，随后进入待机',
    en: { name: 'Waking', desc: 'Eyes slowly crack open with a couple of groggy blinks, then settles into idle' },
    transition: 320,
    pool: [13], poolMs: [800, 800], blinkMs: null,
    sequence: {
      settle: { next: '02' },
      frames: [
        { at: 0,    eyes: { both: { open: 0.1, y: 4 } } },
        { at: 420,  eyes: { left: { open: 0.55, y: 2 }, right: { open: 0.12, y: 4 } } },
        { at: 820,  eyes: { both: { open: 0.3, y: 3 } } },
        { at: 1400, eyes: { both: { open: 1, scaleX: 1.12, scaleY: 1.12, y: -2 } } },
        { at: 2100, eyes: { both: { open: 1, y: 0 } } }
      ]
    }
  },
  {
    id: '02', name: '待机放空', group: 'life',
    desc: '左看看、右看看，目光在两侧各停留片刻，偶尔自旋甩彩带 / 弹跳',
    en: { name: 'Idle', desc: 'Glances left, glances right, lingering on each side; an occasional ribbon spin or bounce' },
    transition: 700,
    pool: [0, 8], poolMs: [9000, 16000], blinkMs: [6000, 14000], antics: true,
    body: { breathe: 0.012 },
    anims: [
      { target: 'eyes', prop: 'lookX', type: 'glance', amp: 10, period: 4800 },
      { target: 'eyes', prop: 'lookY', type: 'sine', amp: 2, period: 4100, phase: 1.1 }
    ]
  },
  {
    id: '03', name: '好奇', group: 'life',
    desc: '圆睁 / 平静 / 扫视眼环快速轮换，头微倾，目光轻轻打量',
    en: { name: 'Curious', desc: 'Wide, calm and scanning eye rings rotate quickly, head tilted, sizing things up' },
    transition: 420,
    pool: [3, 21, 0, 15], poolMs: [1800, 3200], blinkMs: [2500, 5500],
    body: { rotate: 4, breathe: 0.01 },
    eyes: { both: { lookY: -1 } },
    anims: [
      { target: 'eyes', prop: 'lookX', type: 'sine', amp: 2.4, period: 2800 }
    ]
  },
  {
    id: '04', name: '发呆', group: 'life',
    desc: '半闭眼环慢轮换，双眼各望各的，偶尔弹跳提神',
    en: { name: 'Spacing Out', desc: 'Half-closed rings rotate slowly, each eye wandering its own way; a bounce now and then' },
    transition: 800,
    pool: [4, 22, 0], poolMs: [3500, 6000], blinkMs: [4000, 8000], antics: true,
    body: { rotate: -3, breathe: 0.008 },
    eyes: {
      left:  { lookX: -4, lookY: 2 },
      right: { lookX: 5,  lookY: -1 }
    },
    anims: [
      { target: 'eyes', prop: 'lookX', type: 'sine', amp: 3, period: 6800 }
    ]
  },
  {
    id: '05', name: '加载苏醒', group: 'life',
    desc: '双眼缓慢交替亮起，像系统正在逐项初始化',
    en: { name: 'Booting', desc: 'Eyes light up in slow alternation, like a system initializing step by step' },
    transition: 480,
    pool: [0, 8], poolMs: [6000, 10000], blinkMs: null,
    anims: [
      { target: 'left',  prop: 'open', type: 'blink', interval: 1600, dur: 700 },
      { target: 'right', prop: 'open', type: 'blink', interval: 1600, dur: 700, phaseMs: 800 },
      { target: 'eyes',  prop: 'scale', type: 'pulse', amp: 0.04, period: 1600 }
    ]
  },
  {
    id: '06', name: '休眠', group: 'life',
    desc: '困倦眼环 + 半开合，几乎静止，只剩极弱的呼吸起伏',
    en: { name: 'Dormant', desc: 'Drowsy rings at half openness, nearly still, only the faintest breathing' },
    transition: 1200,
    gaze: false,
    pool: [4, 22, 13], poolMs: [4000, 8000], blinkMs: null, openness: 0.4,
    body: { y: 6, scale: 0.98, rotate: -1, breathe: 0.005, color: '#EBE8E1' },
    eyes: { both: { y: 5 } }
  },
  {
    id: '07', name: '抖动唤醒', group: 'life',
    desc: '整球轻颤，闭合眼环交错睁开，随后进入待机',
    en: { name: 'Shake Awake', desc: 'The whole body trembles as the eyes stagger open, then settles into idle' },
    transition: 220,
    pool: [13], poolMs: [800, 800], blinkMs: null,
    body: { breathe: 0.004 },
    anims: [
      { target: 'body', prop: 'x', type: 'jitter', amp: 4.5, speed: 10, decay: 1600 },
      { target: 'body', prop: 'rotate', type: 'jitter', amp: 3, speed: 8, decay: 1600 }
    ],
    sequence: {
      settle: { next: '02' },
      frames: [
        { at: 0,    eyes: { both: { open: 0.1 } } },
        { at: 380,  eyes: { left: { open: 0.4 }, right: { open: 0.12 } } },
        { at: 900,  eyes: { both: { open: 0.7, y: 1 } } },
        { at: 1600, eyes: { both: { open: 1, scaleX: 1.08, scaleY: 1.08, y: -1 } } }
      ]
    }
  },

  /* ==================== 2）情绪反应（12 个） ==================== */

  {
    id: '10', name: '开心', group: 'emotion',
    desc: '笑眼轮换，目光下看看、上看看，身体轻快起伏，偶尔自旋甩彩带',
    en: { name: 'Happy', desc: 'Smiling rings rotate, gaze bobs down and up, body bounces lightly; occasional ribbon spin' },
    transition: 380,
    pool: [2, 11, 17, 19], poolMs: [2500, 4500], blinkMs: [2500, 5000], antics: true,
    body: { y: -3, breathe: 0.014, color: '#F6EFE4' },
    eyes: { both: { y: -3 } },
    anims: [
      { target: 'eyes', prop: 'lookY', type: 'glance', amp: 6, period: 3000 },
      { target: 'body', prop: 'y', type: 'sine', amp: 2.2, period: 1400 }
    ]
  },
  {
    id: '11', name: '疑惑', group: 'emotion',
    desc: '斜眼环轮换，头微倾，一眼放大一眼收小',
    en: { name: 'Puzzled', desc: 'Skeptical rings, head atilt, one eye enlarged and the other shrunk' },
    transition: 420,
    pool: [14, 5, 8], poolMs: [2200, 3800], blinkMs: [2800, 5500],
    body: { rotate: -8, breathe: 0.008 },
    eyes: {
      left:  { y: -4, scaleX: 1.1, scaleY: 1.1 },
      right: { y: 3,  scaleX: 0.9, scaleY: 0.9, lookX: 3 }
    },
    anims: [
      { target: 'body', prop: 'rotate', type: 'sine', amp: 1.4, period: 3200 }
    ]
  },
  {
    id: '12', name: '失落', group: 'emotion',
    desc: '困倦 / 闭合眼环慢轮换，眼睛下沉，目光低垂',
    en: { name: 'Down', desc: 'Drowsy rings rotate slowly, eyes sinking with a downcast gaze' },
    transition: 820,
    pool: [4, 13, 22], poolMs: [4000, 7000], blinkMs: [4000, 8000],
    body: { y: 5, rotate: -4, breathe: 0.007, color: '#EDEAE3' },
    eyes: { both: { y: 8, scaleX: 0.88, scaleY: 0.88, lookY: 4 } },
    anims: [
      { target: 'eyes', prop: 'y', type: 'sine', amp: 1.6, period: 3600 }
    ]
  },
  {
    id: '13', name: '惊讶', group: 'emotion',
    desc: '双眼瞬间放大到 1.45 倍再回稳，身体轻抬定住',
    en: { name: 'Surprised', desc: 'Eyes pop to 1.45x in an instant then steady, the body lifting and freezing' },
    transition: 180,
    pool: [3, 21], poolMs: [2500, 4000], blinkMs: [1800, 3500],
    body: { y: -4, scale: 1.03, breathe: 0.006 },
    eyes: { both: { scaleX: 1.14, scaleY: 1.14, y: -2, lookY: -2 } },
    sequence: {
      settle: 'base',
      frames: [
        { at: 0,   eyes: { both: { scaleX: 0.92, scaleY: 0.92 } } },
        { at: 150, eyes: { both: { scaleX: 1.45, scaleY: 1.45, y: -4 } }, body: { y: -7, scale: 1.05 } },
        { at: 420, eyes: { both: { scaleX: 1.22, scaleY: 1.22, y: -3 } } }
      ]
    }
  },
  {
    id: '14', name: '害羞', group: 'emotion',
    desc: '目光躲向一侧，脸颊在一秒多里渐渐泛起粉色',
    en: { name: 'Shy', desc: 'Gaze slips off to one side as the face slowly blushes pink over a second' },
    transition: 560,
    pool: [0, 24, 13], poolMs: [3000, 5500], blinkMs: [3000, 6000],
    /* base 色 = 序列终态粉，保证静态缩略图与最终观感一致 */
    body: { rotate: 6, breathe: 0.012, color: '#F4D3D0' },
    eyes: { both: { y: 4, lookX: 8, lookY: 3 } },
    anims: [
      { target: 'eyes', prop: 'lookX', type: 'sine', amp: 2, period: 2600 }
    ],
    sequence: {
      settle: 'hold',
      frames: [
        { at: 0,    body: { color: '#F3F0EA' } },
        { at: 1500, body: { color: '#F4D3D0' } }
      ]
    }
  },
  {
    id: '15', name: '疲惫', group: 'emotion',
    desc: '眼皮沉重半睁（低开合度 + 困倦眼环），目光下沉',
    en: { name: 'Tired', desc: 'Heavy eyelids at half openness with drowsy rings, gaze sinking low' },
    transition: 900,
    pool: [4, 22, 13], poolMs: [4000, 8000], blinkMs: null, openness: 0.55,
    body: { y: 4, rotate: -3, breathe: 0.016, color: '#EFECE5' },
    eyes: { both: { y: 5, lookY: 3 } },
    anims: [
      { target: 'eyes', prop: 'open', type: 'sine', amp: 0.06, period: 3400 }
    ]
  },
  {
    id: '16', name: '专注', group: 'emotion',
    desc: '专注眼环族轮换，双眼微微内聚，身体几乎不动',
    en: { name: 'Focused', desc: 'Focused ring family rotates, eyes converge slightly, body almost motionless' },
    transition: 320,
    pool: [7, 16, 11, 10], poolMs: [1800, 3200], blinkMs: [2800, 5500],
    body: { breathe: 0.004 },
    eyes: {
      left:  { x: 4 },
      right: { x: -4 }
    }
  },
  {
    id: '17', name: '慌张', group: 'emotion',
    desc: '圆睁眼环高频轮换，目光乱晃，整球细颤',
    en: { name: 'Panicked', desc: 'Wide rings rotate at high frequency, gaze darting about, the whole body quivering' },
    transition: 200,
    pool: [3, 21], poolMs: [900, 1800], blinkMs: [1200, 3000],
    body: { breathe: 0.006 },
    anims: [
      { target: 'eyes', prop: 'lookX', type: 'jitter', amp: 6, speed: 11 },
      { target: 'eyes', prop: 'lookY', type: 'jitter', amp: 4, speed: 9 },
      { target: 'body', prop: 'x', type: 'jitter', amp: 1.6, speed: 8 }
    ]
  },
  {
    id: '18', name: '无奈', group: 'emotion',
    desc: '斜眼环轮换，头一歪，双眼翻向斜上方',
    en: { name: 'Resigned', desc: 'Sidelong rings, head cocked, both eyes rolling up and away' },
    transition: 560,
    pool: [14, 5, 23], poolMs: [2600, 4500], blinkMs: [4500, 8000],
    body: { rotate: 10, y: 2, breathe: 0.01 },
    eyes: { both: { lookX: 7, lookY: -8 } },
    anims: [
      { target: 'eyes', prop: 'lookY', type: 'sine', amp: 1.6, period: 3000 },
      { target: 'body', prop: 'y', type: 'sine', amp: 1.2, period: 3000 }
    ]
  },
  {
    id: '19', name: '满意', group: 'emotion',
    desc: '目光正视前方，双眼有节奏地上下点动，如同点头认可',
    en: { name: 'Satisfied', desc: 'Gazing straight ahead while the eyes nod up and down in steady approval' },
    transition: 580,
    pool: [15, 8, 2], poolMs: [3500, 6000], blinkMs: [3500, 7000], antics: true,
    body: { breathe: 0.012, color: '#F5EFE6' },
    anims: [
      { target: 'eyes', prop: 'y', type: 'sine', amp: 5, period: 1050 },
      { target: 'body', prop: 'y', type: 'sine', amp: 1.6, period: 1050, phase: 0.6 }
    ]
  },
  {
    id: '20', name: '困惑', group: 'emotion',
    desc: '两眼大小不一，斜眼环轮换，注视方向对不齐',
    en: { name: 'Confused', desc: 'Mismatched eye sizes and sidelong rings, the two gazes never quite aligning' },
    transition: 480,
    pool: [14, 5, 8], poolMs: [2200, 3800], blinkMs: [2800, 5500],
    body: { rotate: -5, breathe: 0.008 },
    eyes: {
      left:  { scaleX: 1.16, scaleY: 1.16, y: -3, lookX: -3 },
      right: { scaleX: 0.8,  scaleY: 0.8,  y: 4,  lookX: 5 }
    },
    anims: [
      { target: 'left',  prop: 'lookX', type: 'sine', amp: 2.5, period: 3200 },
      { target: 'right', prop: 'lookX', type: 'sine', amp: 2.5, period: 3200, phase: 1.6 }
    ]
  },
  {
    id: '21', name: '生气', group: 'emotion',
    desc: '怒目圆睁，脸色在 0.25s 内迅速涨红并保持，身体细微发抖',
    en: { name: 'Angry', desc: 'Glaring eyes; the face flushes red within a quarter second and stays, body trembling' },
    transition: 260,
    pool: [7, 16], poolMs: [2200, 3800], blinkMs: [3500, 7000],
    /* base 色 = 序列终态红，保证静态缩略图与最终观感一致 */
    body: { y: 1, breathe: 0.004, color: '#E4574A' },
    anims: [
      { target: 'body', prop: 'x', type: 'jitter', amp: 1.1, speed: 7 }
    ],
    sequence: {
      settle: 'hold',
      frames: [
        { at: 0,   body: { color: '#F3F0EA' } },
        { at: 250, body: { color: '#E4574A' } }
      ]
    }
  },

  /* ==================== 3）代理工作状态（12 个） ==================== */

  {
    id: '30', name: '思考中', group: 'agent',
    desc: '思考眼环轮换，目光在上方巡回，一条彩带在头顶水平环绕',
    en: { name: 'Thinking', desc: 'Thinking rings rotate, gaze patrolling upward, a ribbon orbiting the head' },
    transition: 480,
    pool: [8, 16, 14, 17, 5], poolMs: [2000, 3600], blinkMs: [3500, 7000],
    body: { rotate: -3, breathe: 0.01, orbit: 1 },
    eyes: { both: { lookY: -6, y: -2 } },
    anims: [
      { target: 'eyes', prop: 'lookX', type: 'sine', amp: 9, period: 2600 }
    ]
  },
  {
    id: '31', name: '接收任务', group: 'agent',
    desc: '轻轻眨一下并放大，像点头确认收到',
    en: { name: 'Receiving', desc: 'A quick blink and slight enlargement, like a nod of acknowledgement' },
    transition: 220,
    pool: [19, 0, 8], poolMs: [4000, 8000], blinkMs: null,
    body: { breathe: 0.008 },
    sequence: {
      settle: 'base',
      frames: [
        { at: 0 },
        { at: 100, eyes: { both: { open: 0.1 } } },
        { at: 280, eyes: { both: { open: 1, scaleX: 1.12, scaleY: 1.12, y: -2 } }, body: { y: -3 } },
        { at: 700, eyes: { both: { open: 1 } } }
      ]
    }
  },
  {
    id: '32', name: '处理中忙碌', group: 'agent',
    desc: '专注眼环轮换，目光小幅循环往复',
    en: { name: 'Busy', desc: 'Focused rings rotate while the gaze loops in tight little circuits' },
    transition: 360,
    pool: [7, 16, 11, 10], poolMs: [1800, 3200], blinkMs: [2800, 5500],
    body: { breathe: 0.008 },
    anims: [
      { target: 'eyes', prop: 'lookX', type: 'sine', amp: 6, period: 1200 },
      { target: 'eyes', prop: 'lookY', type: 'sine', amp: 4, period: 900, phase: 0.8 }
    ]
  },
  {
    id: '33', name: '任务完成', group: 'agent',
    desc: '笑眼轮换 + 自旋甩彩带 + 撒花庆祝',
    en: { name: 'Done', desc: 'Smiling rings, a celebratory ribbon spin and a burst of confetti' },
    transition: 240,
    pool: [2, 8, 17], poolMs: [1400, 2600], blinkMs: [2200, 4500],
    body: { ribbons: 1, confetti: 0.95 },
    eyes: { both: { y: -3 } },
    sequence: {
      settle: 'base',
      frames: [
        { at: 0,   body: { y: 0 } },
        { at: 300, eyes: { both: { scaleX: 1.1, scaleY: 1.1, y: -5 } }, body: { y: -6 } },
        { at: 700, eyes: { both: { scaleX: 1.05, scaleY: 1.05, y: -4 } }, body: { y: -2 } },
        { at: 1100, body: { y: 0 } }
      ]
    }
  },
  {
    id: '34', name: '出错', group: 'agent',
    desc: '圆睁眼环，脸色红白两色急促闪动，最后定格在警示红',
    en: { name: 'Error', desc: 'Wide rings as the face flashes red and white, settling on alarm red' },
    transition: 220,
    pool: [3, 21], poolMs: [2000, 3600], blinkMs: null,
    body: { rotate: -6, color: '#E25B5B' },
    eyes: {
      left:  { y: -3, rotate: -6 },
      right: { y: 4,  rotate: 8 }
    },
    anims: [
      { target: 'eyes', prop: 'lookX', type: 'jitter', amp: 1.4, speed: 8, decay: 800 }
    ],
    sequence: {
      settle: 'hold',
      frames: [
        { at: 0,   body: { color: '#E25B5B', rotate: -6 } },
        { at: 170, body: { color: '#F6F3EC', rotate: -4 } },
        { at: 340, body: { color: '#E25B5B', rotate: -7 } },
        { at: 510, body: { color: '#F6F3EC', rotate: -5 } },
        { at: 700, body: { color: '#DE5555', rotate: -6 } }
      ]
    }
  },
  {
    id: '35', name: '等待输入', group: 'agent',
    desc: '聆听眼环轮换，目光轻轻上下扫读',
    en: { name: 'Listening', desc: 'Listening rings rotate while the gaze sweeps gently up and down' },
    transition: 480,
    pool: [10, 1, 19], poolMs: [2800, 5000], blinkMs: [3000, 7000],
    body: { breathe: 0.01 },
    anims: [
      { target: 'eyes', prop: 'lookY', type: 'sine', amp: 6, period: 2200 }
    ]
  },
  {
    id: '36', name: '联网加载', group: 'agent',
    desc: '左右眼轮流眨，像信号在两端来回跳',
    en: { name: 'Loading', desc: 'Eyes blink in alternation, like a signal hopping between two endpoints' },
    transition: 380,
    pool: [0, 8], poolMs: [6000, 10000], blinkMs: null,
    anims: [
      { target: 'left',  prop: 'open', type: 'blink', interval: 1200, dur: 380 },
      { target: 'right', prop: 'open', type: 'blink', interval: 1200, dur: 380, phaseMs: 600 },
      { target: 'eyes',  prop: 'lookX', type: 'sine', amp: 2, period: 2400 }
    ]
  },
  {
    id: '37', name: '复述回忆', group: 'agent',
    desc: '聆听眼环慢轮换，目光飘向上方翻检记忆',
    en: { name: 'Recalling', desc: 'Listening rings rotate slowly as the gaze drifts upward, leafing through memory' },
    transition: 780,
    pool: [10, 1, 19], poolMs: [4000, 8000], blinkMs: null,
    body: { rotate: -2, breathe: 0.009 },
    eyes: { both: { lookY: -9, lookX: 3, y: -3 } },
    anims: [
      { target: 'eyes', prop: 'lookY', type: 'sine', amp: 2.4, period: 4000 }
    ]
  },
  {
    id: '38', name: '拒绝/受限', group: 'agent',
    desc: '斜眼下压，进入时连续摇头，明确表示不行',
    en: { name: 'Refusing', desc: 'A lowered sidelong gaze with a firm head-shake on entry: the answer is no' },
    transition: 380,
    pool: [14, 5, 23], poolMs: [2600, 4500], blinkMs: [4500, 8000], openness: 0.6,
    body: { y: 2, rotate: -2, color: '#EFE8E4' },
    eyes: { both: { lookY: 3, y: 2 } },
    sequence: {
      settle: 'base',
      frames: [
        { at: 0,   body: { x: 0 } },
        { at: 130, body: { x: -9, rotate: -6 } },
        { at: 300, body: { x: 8,  rotate: 2 } },
        { at: 470, body: { x: -6, rotate: -5 } },
        { at: 630, body: { x: 4,  rotate: 0 } },
        { at: 800, body: { x: 0,  rotate: -2 } }
      ]
    }
  },
  {
    id: '39', name: '输出回复', group: 'agent',
    desc: '扫读眼环轮换，随输出节奏轻微缩放',
    en: { name: 'Replying', desc: 'Reading rings rotate with a gentle pulse that keeps time with the output' },
    transition: 360,
    pool: [15, 9], poolMs: [4000, 8000], blinkMs: null,
    body: { breathe: 0.008 },
    eyes: { both: { y: -2 } },
    anims: [
      { target: 'eyes', prop: 'scale', type: 'pulse', amp: 0.1, period: 680 },
      { target: 'body', prop: 'y', type: 'sine', amp: 1.2, period: 680 }
    ]
  },
  {
    id: '40', name: '检索资料', group: 'agent',
    desc: '6 组扫读眼环高速轮换（弹簧加速），目光左右快扫',
    en: { name: 'Searching', desc: 'Six reading rings rotate at high speed while the gaze sweeps rapidly side to side' },
    transition: 320,
    pool: [15, 9, 3, 20, 12, 18], poolMs: [1000, 1800], poolSpeed: 10, blinkMs: [1600, 4000],
    body: { breathe: 0.006 },
    anims: [
      { target: 'eyes', prop: 'lookX', type: 'scan', amp: 11, period: 700 }
    ]
  },
  {
    id: '41', name: '停止终止', group: 'agent',
    desc: '闭合眼环，慢慢收小半闭后定格',
    en: { name: 'Powering Off', desc: 'Closing rings shrink to half-closed and quietly freeze' },
    transition: 280,
    gaze: false,
    pool: [13, 22], poolMs: [6000, 9000], blinkMs: null,
    body: { y: 3, breathe: 0.004, color: '#EBE8E2' },
    sequence: {
      settle: 'hold',
      frames: [
        { at: 0 },
        { at: 1500, eyes: { both: { scaleX: 0.6, scaleY: 0.6, open: 0.35, y: 3 } }, body: { y: 4, scale: 0.97 } }
      ]
    }
  }
];


/* ============================================================
 * ball.js —— 渲染层（纯渲染，不含业务逻辑）
 *
 *   坐标系：viewBox -15 -15 259 259，头部中心 HEAD_C = 114.2705
 *   身体：形状轮廓环（blob 圆胖 / wedge 三角 / gem 菱形）折线路径
 *   眼睛：25 组表情眼环（48 点轮廓），由 engine 逐点插值后传入，
 *        本层负责球面投影、变换与 path 更新
 *   球面投影：按眼睛当前高度采样身体轮廓的局部半宽，经度换算 + 余弦压缩，
 *            自旋偏航时眼睛绕到背面自动隐藏（cos <= 0.02 判定）
 *   彩带：两种形态 ——
 *        自旋甩带（角速度达阈值时甩出、减速后回缩的 3D 轨道拖尾）
 *        常驻环带（低倾角水平轨道持续环绕，用于"思考中"等状态）
 *        均使用 5-stop 色相漂移渐变 + 头宽尾细轮廓 + 圆头封口
 *   撒花：一次性物理粒子（速度衰减 + 微重力 + 金色五角星混入）
 *   zzz：睡眠状态右上角循环漂浮的字母粒子
 * ============================================================ */
(function () {
  'use strict';

  var EB = (window.EmotionBall = window.EmotionBall || {});
  var RD = window.EB_RINGS;
  var SVGNS = 'http://www.w3.org/2000/svg';
  var uid = 0;
  var TAU = Math.PI * 2;

  var HEAD_C = RD.HEAD_C;          /* 114.2705 */
  var EYE_HALF = RD.EYE_HALF;      /* 21 */
  var EXPR = RD.EXPRESSIONS;
  var STAR_GOLD = RD.STAR_GOLD;
  var CONFETTI_COLORS = ['#f9705c', '#5b95f0', '#3fbe86', '#f5b13f', '#9a72ee', '#35c3bd'];
  /* 五角星 path（内径比 0.42） */
  var STAR_PATH = (function () {
    var pts = [];
    for (var e = 0; e < 10; e++) {
      var a = -Math.PI / 2 + e * Math.PI / 5;
      var r = e % 2 === 0 ? 1 : 0.42;
      pts.push((Math.cos(a) * r).toFixed(3) + ' ' + (Math.sin(a) * r).toFixed(3));
    }
    return 'M' + pts.join('L') + 'Z';
  })();

  function el(tag, attrs) {
    var node = document.createElementNS(SVGNS, tag);
    for (var k in attrs) node.setAttribute(k, attrs[k]);
    return node;
  }
  function r2(v) { return Math.round(v * 100) / 100; }
  function clamp(v, a, b) { return v < a ? a : (v > b ? b : v); }
  function rand(a, b) { return a + Math.random() * (b - a); }
  function shade(hex, amt) {
    var h = hex.replace('#', '');
    if (h.length === 3) h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2];
    var n = parseInt(h, 16);
    var r = (n >> 16) & 255, g = (n >> 8) & 255, b = n & 255;
    var target = amt < 0 ? 0 : 255;
    var a = Math.abs(amt);
    r = Math.round(r + (target - r) * a);
    g = Math.round(g + (target - g) * a);
    b = Math.round(b + (target - b) * a);
    return '#' + ((1 << 24) | (r << 16) | (g << 8) | b).toString(16).slice(1);
  }

  /* 轮廓环 → 闭合折线 path；48 点密度下视觉平滑 */
  function ringPath(ring) {
    var s = 'M';
    for (var i = 0; i < ring.length; i++) {
      s += (i ? 'L' : '') + ring[i][0].toFixed(2) + ' ' + ring[i][1].toFixed(2);
    }
    return s + 'Z';
  }
  function centroid(ring) {
    var x = 0, y = 0;
    for (var i = 0; i < ring.length; i++) { x += ring[i][0]; y += ring[i][1]; }
    return [x / ring.length, y / ring.length];
  }

  function createBall(container, opts) {
    opts = opts || {};
    var id = 'eb' + (uid++);
    var lite = !!opts.lite;
    var shape = RD.SHAPES[opts.shape] || RD.SHAPES.blob;
    var face = shape.face;
    var headRing = shape.ring;

    /* ---- 形状轮廓采样：每 2px 一行的 [minX, maxX]，供眼睛贴合任意身体轮廓 ---- */
    var silMinY = 1e9, silMaxY = -1e9;
    var i, p;
    for (i = 0; i < headRing.length; i++) {
      if (headRing[i][1] < silMinY) silMinY = headRing[i][1];
      if (headRing[i][1] > silMaxY) silMaxY = headRing[i][1];
    }
    var SIL_STEP = 2;
    var silRows = [];
    (function buildSil() {
      var rows = Math.ceil((silMaxY - silMinY) / SIL_STEP) + 1;
      for (var r = 0; r < rows; r++) {
        var y = silMinY + r * SIL_STEP;
        var lo = 1e9, hi = -1e9;
        for (var e = 0; e < headRing.length; e++) {
          var a = headRing[e], b = headRing[(e + 1) % headRing.length];
          var y0 = a[1], y1 = b[1];
          if ((y0 <= y && y1 >= y) || (y1 <= y && y0 >= y)) {
            var t = y1 === y0 ? 0 : (y - y0) / (y1 - y0);
            var x = a[0] + (b[0] - a[0]) * t;
            if (x < lo) lo = x;
            if (x > hi) hi = x;
          }
        }
        if (lo > hi) { lo = HEAD_C - 4; hi = HEAD_C + 4; }
        silRows.push([lo, hi]);
      }
    })();
    function silAt(y) {
      var r = Math.round((clamp(y, silMinY, silMaxY) - silMinY) / SIL_STEP);
      return silRows[clamp(r, 0, silRows.length - 1)];
    }

    /* ---- SVG 骨架 ---- */
    var svg = el('svg', {
      viewBox: '-15 -15 259 259',
      width: '100%',
      height: '100%',
      role: 'img',
      'aria-label': opts.label || 'AI 表情小球'
    });
    svg.style.display = 'block';
    svg.style.overflow = 'visible';

    var defs = el('defs', {});
    var grad = el('radialGradient', { id: id + 'g', cx: '38%', cy: '32%', r: '75%' });
    var stopA = el('stop', { offset: '0%' });
    var stopB = el('stop', { offset: '62%' });
    var stopC = el('stop', { offset: '100%' });
    grad.appendChild(stopA); grad.appendChild(stopB); grad.appendChild(stopC);
    defs.appendChild(grad);
    svg.appendChild(defs);

    var fxBack = el('g', { 'pointer-events': 'none' });
    svg.appendChild(fxBack);

    var bodyG = el('g', {});
    var head = el('path', { d: ringPath(headRing), fill: 'url(#' + id + 'g)', stroke: 'none', 'stroke-width': '2' });
    bodyG.appendChild(head);

    function buildEye(k) {
      var node = el('path', { fill: '#1A1A1A', stroke: 'none', 'stroke-width': '1.6' });
      node.setAttribute('d', ringPath(EXPR[0][k]));
      return { node: node, ring: EXPR[0][k], c: centroid(EXPR[0][k]) };
    }
    var eyeL = buildEye(0);
    var eyeR = buildEye(1);
    bodyG.appendChild(eyeL.node);
    bodyG.appendChild(eyeR.node);
    svg.appendChild(bodyG);

    var fxFront = el('g', { 'pointer-events': 'none' });
    svg.appendChild(fxFront);

    /* 眼睛基准中心：默认表情环的质心 */
    var BASE_C = [centroid(EXPR[0][0]), centroid(EXPR[0][1])];

    /* ---- zzz 睡眠粒子：三枚字母沿右上方向循环漂浮 ---- */
    var zzzNodes = null;
    if (!lite) {
      zzzNodes = [];
      for (var zi = 0; zi < 3; zi++) {
        var zn = el('text', {
          x: 0, y: 0, fill: '#A8A296', opacity: '0',
          'font-family': "'Space Grotesk', 'Noto Sans SC', sans-serif",
          'font-weight': '700', 'font-style': 'italic', 'text-anchor': 'middle'
        });
        zn.textContent = 'z';
        fxFront.appendChild(zn);
        zzzNodes.push(zn);
      }
    }

    container.appendChild(svg);

    /* ---- 彩带：3D 轨道拖尾 ---- */
    var trails = [];
    var planes = [];
    var planeG = 4;
    var baseHue = 0;
    var spawnAt = [];
    var spawnIdx = 0;
    var wasFast = false;
    var prevYaw = 0, prevNow = 0;
    var orbitNextAt = 0;
    var confPieces = [];

    function makePlanes() {
      var base = rand(-0.85, 0.85);
      planes = [{ tilt: rand(0.16, 0.5), roll: base + rand(-0.12, 0.12) }];
      planeG = Math.round(rand(3, 5));
      baseHue = rand(0, 360);
      spawnIdx = 0;
    }

    function orbitPoint(o, lam) {
      var hx = o.rad * Math.sin(lam);
      var hy = -o.rad * Math.cos(lam) * Math.sin(o.tilt);
      var ca = Math.cos(o.roll), sa = Math.sin(o.roll);
      return {
        x: HEAD_C + hx * ca - hy * sa,
        y: HEAD_C + hx * sa + hy * ca,
        z: Math.cos(lam) * Math.cos(o.tilt),
        l: lam
      };
    }

    /** 创建一条拖尾：独立 5-stop 渐变 + 前后两段 path */
    function createTrail(cfg) {
      if (trails.length > 8) return;
      var gradEl = el('linearGradient', { id: id + 'tg' + (uid++), gradientUnits: 'userSpaceOnUse' });
      var stops = [];
      for (var s = 0; s < 5; s++) {
        var st = el('stop', { offset: (s / 4).toFixed(3) });
        gradEl.appendChild(st);
        stops.push(st);
      }
      defs.appendChild(gradEl);
      var fill = 'url(#' + gradEl.getAttribute('id') + ')';
      var back = el('path', { stroke: 'none', fill: fill, opacity: '0' });
      var front = el('path', { stroke: 'none', fill: fill, opacity: '0' });
      fxBack.appendChild(back);
      fxFront.appendChild(front);
      trails.push({
        o: cfg.o, r: cfg.r, life: 0, ret: 0, hist: [],
        orbitMode: !!cfg.orbit,
        hue: cfg.hue,
        hueSpan: rand(45, 95) * (Math.random() < 0.5 ? 1 : -1),
        hueVel: rand(18, 42) * (Math.random() < 0.5 ? 1 : -1),
        gradEl: gradEl, stops: stops, back: back, front: front
      });
    }

    /** 自旋甩带：沿本次自旋的随机轨道平面错峰甩出 */
    function spawnTrail(lam0, dir) {
      var pl = planes[0];
      var tierStep = 38 / Math.max(planeG - 1, 1);
      var rw = planeG <= 3 ? rand(8, 10.5) : planeG === 4 ? rand(6.6, 8.6) : rand(5.6, 7.4);
      createTrail({
        o: {
          lam: lam0, lamVel: dir * rand(0.5, 1.1),
          tilt: pl.tilt + rand(-0.04, 0.04),
          roll: pl.roll + rand(-0.05, 0.05),
          rad: 116 + spawnIdx * tierStep + rand(-1.5, 1.5),
          radVel: rand(0, 2.5),
          follow: rand(0.74, 0.94),
          carry: 0,
          arc: rand(2.2, 3.4)
        },
        r: rw,
        hue: baseHue + 360 * spawnIdx / Math.max(planeG, 1) + rand(-14, 14)
      });
      spawnIdx++;
    }

    /** 常驻环带：低倾角水平轨道匀速环绕（"思考中"等状态的持续效果） */
    function spawnOrbit(idx) {
      createTrail({
        orbit: true,
        o: {
          lam: rand(0, TAU),
          lamVel: (Math.random() < 0.5 ? -1 : 1) * rand(1.7, 2.3),
          tilt: rand(0.1, 0.22),
          roll: rand(-0.12, 0.12),
          rad: 124 + idx * 16,
          radVel: 0,
          follow: 0.8,
          carry: 0,
          arc: rand(2.4, 3.2)
        },
        r: rand(5.5, 7),
        hue: rand(0, 360)
      });
    }

    /* 拖尾轮廓：头宽尾细 + 首尾圆头封口，按 z 正负拆为前 / 后两段 */
    function buildTrail(pts, width) {
      var n = pts.length;
      var nx = [], ny = [], e;
      for (e = 0; e < n; e++) {
        var p0 = pts[e > 0 ? e - 1 : 0], p1 = pts[e < n - 1 ? e + 1 : n - 1];
        var dx = p1.x - p0.x, dy = p1.y - p0.y;
        var h = Math.hypot(dx, dy) || 1;
        dx /= h; dy /= h;
        var d = width * (0.5 + (e / (n - 1)) * 0.5) / 2;
        nx.push(-dy * d); ny.push(dx * d);
      }
      function cap(idx) {
        var hw = Math.max(Math.hypot(nx[idx], ny[idx]), 0.2);
        return 'A' + r2(hw) + ' ' + r2(hw) + ' 0 0 0 ';
      }
      function seg(a, b) {
        var s = '', k;
        for (k = a; k <= b; k++) s += (k === a ? 'M' : 'L') + r2(pts[k].x + nx[k]) + ' ' + r2(pts[k].y + ny[k]);
        s += b === n - 1 ? cap(b) : 'L';
        for (k = b; k >= a; k--) s += (k === b ? '' : 'L') + r2(pts[k].x - nx[k]) + ' ' + r2(pts[k].y - ny[k]);
        if (a === 0) s += cap(0) + r2(pts[0].x + nx[0]) + ' ' + r2(pts[0].y + ny[0]);
        return s + 'Z';
      }
      var front = '', back = '', d0 = 0;
      while (d0 < n) {
        var isF = pts[d0].z >= 0;
        var i2 = d0;
        while (i2 + 1 < n && (pts[i2 + 1].z >= 0) === isF) i2++;
        var a2 = Math.max(d0 - 1, 0), b2 = Math.min(i2 + 1, n - 1);
        if (b2 > a2) {
          var str = seg(a2, b2);
          if (isF) front += str; else back += str;
        }
        d0 = i2 + 1;
      }
      return { front: front, back: back };
    }

    function removeTrail(idx) {
      var rb = trails[idx];
      rb.back.remove(); rb.front.remove(); rb.gradEl.remove();
      trails.splice(idx, 1);
    }

    /* ---- 撒花：一次性物理粒子爆发 ---- */
    function burst(count) {
      if (lite) return;
      count = count || 20;
      for (var i = 0; i < count && confPieces.length < 60; i++) {
        var ang = (i / count) * TAU + rand(-0.35, 0.35);
        var spd = rand(170, 360);
        var star = Math.random() < 0.18;
        var round = !star && Math.random() < 0.3;
        var node;
        if (star) node = el('path', { d: STAR_PATH, fill: STAR_GOLD });
        else if (round) node = el('circle', { r: 1, fill: CONFETTI_COLORS[(Math.random() * CONFETTI_COLORS.length) | 0] });
        else node = el('rect', { x: -0.5, y: -0.5, width: 1, height: 1, rx: 0.24, fill: CONFETTI_COLORS[(Math.random() * CONFETTI_COLORS.length) | 0] });
        fxFront.appendChild(node);
        confPieces.push({
          x: HEAD_C + Math.cos(ang) * rand(96, 116),
          y: HEAD_C + Math.sin(ang) * rand(96, 116),
          vx: Math.cos(ang) * spd,
          vy: Math.sin(ang) * spd - rand(20, 75),
          life: 0, max: rand(0.45, 0.85),
          r: star ? rand(4, 7) : rand(3.5, 8),
          rot: rand(0, 360), vr: rand(-260, 260),
          stretch: (!star && !round) ? 1.9 : 1,
          el: node
        });
      }
    }

    /* ---- 状态缓存 ---- */
    var curBodyColor = null;
    var curSketch = -1;

    function setBodyColor(color) {
      if (color === curBodyColor) return;
      curBodyColor = color;
      stopA.setAttribute('stop-color', shade(color, 0.22));
      stopB.setAttribute('stop-color', color);
      stopC.setAttribute('stop-color', shade(color, -0.12));
      if (curSketch > 0.5) head.setAttribute('stroke', shade(color, -0.6));
    }

    /* ---- 眼睛：轮廓环形变 + 球面投影 ---- */
    function setEye(eye, pose, k, sketch, yaw) {
      /* d 更新：engine 传入插值后的环（引用不变则跳过）。
       * 只接受安全平行眼环的插值；左右位置和开合受统一 pose gate 约束。 */
      var ring = pose.ring;
      if (ring && ring !== eye.ring) {
        eye.ring = ring;
        eye.node.setAttribute('d', ringPath(ring));
        eye.c = centroid(ring);
      }

      var base = eye.c || BASE_C[k];
      var open = clamp(pose.open, 0.02, 2.4);
      var sy = clamp(pose.scaleY * open * face.eye, 0.02, 2.4);
      var sxBase = pose.scaleX * face.eye;

      /* 纵向：脸部拟合映射 + 轮廓钳制 */
      var halfH = EYE_HALF * sy + 2;
      var ey0 = HEAD_C + face.y + (base[1] - HEAD_C) * face.sy + pose.y + pose.lookY;
      ey0 = clamp(ey0, silMinY + halfH, silMaxY - halfH);

      var sil = silAt(ey0);
      var cx0 = (sil[0] + sil[1]) / 2;
      var hw = Math.max((sil[1] - sil[0]) / 2, 12);

      /* 横向：经度换算 + 自旋偏航 + 余弦压缩 */
      var ox = face.x + (base[0] - HEAD_C) * face.sx + pose.x + pose.lookX;
      var theta = clamp(ox / hw, -1.15, 1.15);
      var total = theta + (yaw || 0);
      var cn = Math.cos(total);
      if (cn <= 0.02) {
        eye.node.style.display = 'none';
        return;
      }
      eye.node.style.display = '';
      var ex = cx0 + hw * Math.sin(total) * 0.985;
      var dyN = (ey0 - HEAD_C) / 130;
      var fy = Math.sqrt(1 - dyN * dyN * 0.22);

      eye.node.setAttribute('transform',
        'translate(' + r2(ex) + ' ' + r2(ey0) + ')' +
        (pose.rotate ? ' rotate(' + r2(pose.rotate) + ')' : '') +
        ' scale(' + r2(sxBase * cn) + ' ' + r2(sy * fy) + ')' +
        ' translate(' + r2(-base[0]) + ' ' + r2(-base[1]) + ')');

      var fill = sketch > 0.5 ? 'none' : pose.color;
      var stroke = sketch > 0.5 ? pose.color : 'none';
      if (fill !== eye.lastFill) { eye.node.setAttribute('fill', fill); eye.lastFill = fill; }
      if (stroke !== eye.lastStroke) { eye.node.setAttribute('stroke', stroke); eye.lastStroke = stroke; }
    }

    /* ---- 每帧 ---- */
    function applyPose(pose) {
      window.EB_SAFE_FACE.constrain(pose);
      var b = pose.body;
      var now = performance.now();
      var sketch = b.sketch || 0;

      bodyG.setAttribute('transform',
        'translate(' + r2(HEAD_C + b.x) + ' ' + r2(HEAD_C + b.y) + ')' +
        ' rotate(' + r2(b.rotate || 0) + ')' +
        ' scale(' + r2(b.scale) + ')' +
        ' translate(' + r2(-HEAD_C) + ' ' + r2(-HEAD_C) + ')');
      setBodyColor(b.color);

      if (sketch !== curSketch) {
        curSketch = sketch;
        if (sketch > 0.5) {
          /* 线稿模式：轮廓描边取体色加深，浅色页面上依然清晰 */
          head.setAttribute('fill', 'none');
          head.setAttribute('stroke', shade(b.color, -0.6));
          head.setAttribute('stroke-opacity', '0.85');
        } else {
          head.setAttribute('fill', 'url(#' + id + 'g)');
          head.setAttribute('stroke', 'none');
        }
      }

      var yaw = b.yaw || 0;
      setEye(eyeL, pose.left, 0, sketch, yaw);
      setEye(eyeR, pose.right, 1, sketch, yaw);

      if (lite) return;

      var dt = prevNow ? clamp((now - prevNow) / 1000, 0.001, 0.05) : 1 / 60;
      prevNow = now;

      /* ---- zzz 睡眠粒子：三枚字母错峰沿右上方向漂浮，先淡入后淡出 ---- */
      if (zzzNodes) {
        var zOn = (b.zzz || 0) > 0;
        for (var z = 0; z < zzzNodes.length; z++) {
          var znode = zzzNodes[z];
          if (!zOn) {
            if (znode.getAttribute('opacity') !== '0') znode.setAttribute('opacity', '0');
            continue;
          }
          var zp = (now * 0.00033 + z / 3) % 1;
          var zo = (zp < 0.18 ? zp / 0.18 : 1 - (zp - 0.18) / 0.82) * 0.8 * b.zzz;
          znode.setAttribute('opacity', zo.toFixed(3));
          znode.setAttribute('font-size', (12 + zp * 11).toFixed(1));
          znode.setAttribute('transform',
            'translate(' + r2(180 + zp * 34 + 4 * Math.sin(zp * 9)) + ' ' + r2(48 - zp * 42) + ')' +
            ' rotate(' + r2(-10 + zp * 14) + ')');
        }
      }

      /* ---- 自旋角速度（甩带触发源） ---- */
      var dYaw = yaw - prevYaw;
      if (!isFinite(dYaw) || Math.abs(dYaw) > 1.2) dYaw = 0;
      prevYaw = yaw;
      var vel = dYaw / dt;
      var fast = Math.abs(vel) >= 0.9;
      var dir = vel >= 0 ? 1 : -1;

      if (fast && !wasFast) {
        makePlanes();
        spawnAt = [];
        for (var q = 0; q < planeG; q++) spawnAt.push(now + q * rand(55, 105));
      }
      if (!fast) spawnAt.length = 0;
      wasFast = fast;
      if (Math.abs(vel) >= 5) {
        while (spawnAt.length && now >= spawnAt[0]) {
          spawnAt.shift();
          spawnTrail(yaw - rand(0, 0.18) * dir, dir);
        }
      }

      /* ---- 常驻环带补给：状态需要且数量不足时错峰生成 ---- */
      var orbitWant = (b.orbit || 0) > 0;
      if (orbitWant && now >= orbitNextAt) {
        var orbitCount = 0;
        for (var oc = 0; oc < trails.length; oc++) if (trails[oc].orbitMode) orbitCount++;
        if (orbitCount < 2) spawnOrbit(orbitCount);
        orbitNextAt = now + 700;
      }

      /* ---- 彩带逐帧更新 ---- */
      for (var ti = trails.length - 1; ti >= 0; ti--) {
        var rb = trails[ti];
        rb.life += dt;
        var retract = rb.orbitMode ? !orbitWant : (!fast || rb.life > 5);
        rb.ret = clamp(rb.ret + (retract ? dt / 0.5 : -dt / 0.35), 0, 1);
        if (retract && rb.ret >= 1) { removeTrail(ti); continue; }
        var o = rb.o;
        if (rb.orbitMode) {
          /* 环带：匀速环绕，叠加少量自旋跟随 */
          o.lam += o.lamVel * dt + dYaw * o.follow;
        } else if (fast) {
          o.carry = vel * o.follow;
          o.lam += dYaw * o.follow + o.lamVel * dt;
        } else {
          o.lam += (o.carry + o.lamVel) * dt;
          o.carry *= Math.exp(-2.6 * dt);
          o.lamVel *= Math.exp(-2.6 * dt);
        }
        o.rad += o.radVel * dt;

        var hist = rb.hist;
        var lastL = hist.length ? hist[hist.length - 1].l : o.lam - 0.001 * dir;
        var dl = o.lam - lastL;
        var steps = Math.min(Math.ceil(Math.abs(dl) / 0.09), 24);
        for (var st = 1; st <= steps; st++) hist.push(orbitPoint(o, lastL + dl * st / steps));
        if (!hist.length) hist.push(orbitPoint(o, o.lam));

        /* 回缩：smoothstep 弧长收窄 + 首点插值细修 + 上限 48 点 */
        var span = o.arc * (1 - rb.ret * rb.ret * (3 - 2 * rb.ret));
        while (hist.length > 2 && Math.abs(o.lam - hist[0].l) > span) hist.shift();
        var over = Math.abs(o.lam - hist[0].l) - span;
        if (hist.length >= 2 && over > 0) {
          var tl = hist[0].l + (o.lam - hist[0].l >= 0 ? 1 : -1) * over;
          hist[0] = orbitPoint(o, tl);
        }
        if (hist.length > 48) hist.splice(0, hist.length - 48);

        var zHead = Math.cos(o.lam) * Math.cos(o.tilt);
        var pz = 0.72 + 0.28 * clamp(zHead, 0, 1);
        var grow = Math.min(rb.life / 0.34, 1);
        grow = grow * grow * (3 - 2 * grow);
        var width = rb.r * pz * 1.7 * grow * (1 - 0.72 * rb.ret * rb.ret);
        var fade = Math.min(rb.life / 0.26, 1).toFixed(3);

        if (hist.length < 2 || width < 0.5) {
          rb.back.setAttribute('opacity', '0');
          rb.front.setAttribute('opacity', '0');
          continue;
        }
        var dstr = buildTrail(hist, width);
        rb.back.setAttribute('d', dstr.back);
        rb.front.setAttribute('d', dstr.front);
        rb.back.setAttribute('opacity', fade);
        rb.front.setAttribute('opacity', fade);

        /* 5-stop 色相漂移渐变，端点跟随拖尾首尾 */
        var hue = rb.hue + rb.hueVel * rb.life;
        for (var si = 0; si < rb.stops.length; si++) {
          var frac = si / (rb.stops.length - 1);
          var hv = hue + frac * rb.hueSpan;
          rb.stops[si].setAttribute('stop-color',
            'hsl(' + (((hv % 360) + 360) % 360).toFixed(0) + ' 56% ' + (56 + 11 * frac).toFixed(0) + '%)');
        }
        var tail = hist[0], headP = hist[hist.length - 1];
        rb.gradEl.setAttribute('x1', tail.x.toFixed(1));
        rb.gradEl.setAttribute('y1', tail.y.toFixed(1));
        rb.gradEl.setAttribute('x2', headP.x.toFixed(1));
        rb.gradEl.setAttribute('y2', headP.y.toFixed(1));
      }

      /* ---- 撒花更新：速度衰减 0.94^60dt + 微重力 40/s ---- */
      for (var ci = confPieces.length - 1; ci >= 0; ci--) {
        var pc = confPieces[ci];
        pc.life += dt;
        if (pc.life >= pc.max) {
          pc.el.remove();
          confPieces.splice(ci, 1);
          continue;
        }
        pc.x += pc.vx * dt;
        pc.y += pc.vy * dt;
        var drag = Math.pow(0.94, 60 * dt);
        pc.vx *= drag;
        pc.vy = pc.vy * drag + 40 * dt;
        pc.rot += pc.vr * dt;
        var u = pc.life / pc.max;
        var fd = u < 0.1 ? u / 0.1 : Math.pow(1 - (u - 0.1) / 0.9, 1.7);
        var sz = Math.max(pc.r * (1 - 0.4 * u), 0.5);
        pc.el.setAttribute('opacity', fd.toFixed(3));
        pc.el.setAttribute('transform',
          'translate(' + r2(pc.x) + ' ' + r2(pc.y) + ') rotate(' + r2(pc.rot) + ') scale(' + r2(sz) + ' ' + r2(sz * pc.stretch) + ')');
      }
    }

    function destroy() {
      if (svg.parentNode) svg.parentNode.removeChild(svg);
    }

    return { svg: svg, applyPose: applyPose, burst: burst, destroy: destroy };
  }

  EB.createBall = createBall;
})();


/* ============================================================
 * engine.js —— 驱动层（依赖 ball.js；消费 emotions.js 纯数据）
 *
 * 职责：
 *   1. 配置注册中心 EmotionBall.config：校验 / 深合并默认值 / 导入导出
 *   2. EmotionEngine：rAF 状态机 + 动画原语 + 平滑插值 + 兜底 + 待机策略
 *   3. 对外 SDK：EmotionBall.create(el, opts) → engine 实例
 *
 * 对外 API（宿主工具只依赖这一层）：
 *   const ball = EmotionBall.create(el, { emotion:'02', idle:true });
 *   ball.setEmotion('30');
 *   ball.handleAIMessage({ emotionId:'30', tips:'正在思考' });   // 或 JSON 字符串
 *   ball.on('change'|'tips'|'error', cb);
 *   ball.startTour(ids, interval) / ball.stopTour();
 *   ball.registerEmotion(config); ball.destroy();
 *   EmotionBall.config.exportConfig() / importConfig(json);
 * ============================================================ */
(function () {
  'use strict';

  var EB = (window.EmotionBall = window.EmotionBall || {});
  var RD = window.EB_RINGS;
  var EXPR = RD.EXPRESSIONS;
  var TAU = Math.PI * 2;
  var FALLBACK_ID = '02';

  /* ---------------- 基础工具 ---------------- */

  function clamp(v, a, b) { return v < a ? a : (v > b ? b : v); }
  function lerp(a, b, t) { return a + (b - a) * t; }
  function rand(a, b) { return a + Math.random() * (b - a); }
  function easeInOutCubic(t) {
    return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
  }

  /* 临界阻尼弹簧步进：springStep(spring, 频率, 阻尼比, dt)，子步 1/120 保证数值稳定 */
  function spring(v0) { return { x: v0, v: 0, t: v0 }; }
  function springStep(s, w, z, dt) {
    s.v += (-2 * z * w * s.v - w * w * (s.x - s.t)) * dt;
    s.x += s.v * dt;
    if (!isFinite(s.x) || !isFinite(s.v)) { s.x = s.t; s.v = 0; }
  }

  /* 两组眼环逐点插值 */
  function lerpRing(a, b, t) {
    var out = new Array(a.length);
    for (var i = 0; i < a.length; i++) {
      out[i] = [a[i][0] + (b[i][0] - a[i][0]) * t, a[i][1] + (b[i][1] - a[i][1]) * t];
    }
    return out;
  }

  /* 弹跳：4 段递减抛物线（高度 48/28/14/6，时长 0.5/0.382/0.27/0.177s） */
  var BOUNCE_SEGS = [{ h: 48, d: 0.5 }, { h: 28, d: 0.382 }, { h: 14, d: 0.27 }, { h: 6, d: 0.177 }];
  var BOUNCE_TOTAL = BOUNCE_SEGS.reduce(function (s, q) { return s + q.d; }, 0);

  function hexToRgb(hex) {
    var h = hex.replace('#', '');
    if (h.length === 3) h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2];
    var n = parseInt(h, 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  }
  function rgbToHex(r, g, b) {
    return '#' + [r, g, b].map(function (v) {
      return clamp(Math.round(v), 0, 255).toString(16).padStart(2, '0');
    }).join('');
  }
  function lerpColor(a, b, t) {
    if (a === b) return b;
    var A = hexToRgb(a), B = hexToRgb(b);
    return rgbToHex(lerp(A[0], B[0], t), lerp(A[1], B[1], t), lerp(A[2], B[2], t));
  }

  /* ---------------- Pose：默认值 / 合并 / 插值 ---------------- */

  var DEFAULT_BODY = {
    x: 0, y: 0, scale: 1, rotate: 0, color: '#F3F0EA', breathe: 0.01,
    ribbons: 0, confetti: 0, sketch: 0,
    zzz: 0,      /* 睡眠字母粒子（0~1） */
    orbit: 0     /* 常驻水平环带（0~1） */
  };
  var DEFAULT_EYE = { x: 0, y: 0, scaleX: 1, scaleY: 1, rotate: 0, open: 1, color: '#1A1A1A', lookX: 0, lookY: 0 };

  /* 眼环数据自带左右不对称，默认姿态不叠加高低差 */
  function defaultPose() {
    return {
      body: Object.assign({}, DEFAULT_BODY),
      left: Object.assign({}, DEFAULT_EYE),
      right: Object.assign({}, DEFAULT_EYE)
    };
  }
  function clonePose(p) {
    return {
      body: Object.assign({}, p.body),
      left: Object.assign({}, p.left),
      right: Object.assign({}, p.right)
    };
  }

  /** 把配置片段（body / eyes.both / eyes.left / eyes.right）合并到 pose 上（原地修改） */
  function applySpec(pose, spec) {
    if (!spec) return pose;
    if (spec.body) Object.assign(pose.body, spec.body);
    var e = spec.eyes;
    if (e) {
      if (e.both) { Object.assign(pose.left, e.both); Object.assign(pose.right, e.both); }
      if (e.left) Object.assign(pose.left, e.left);
      if (e.right) Object.assign(pose.right, e.right);
    }
    return pose;
  }

  function lerpPose(a, b, t) {
    var out = defaultPose();
    ['body', 'left', 'right'].forEach(function (part) {
      var pa = a[part], pb = b[part], po = out[part];
      for (var k in pb) {
        var vb = pb[k];
        if (typeof vb === 'number') po[k] = lerp(pa[k] != null ? pa[k] : vb, vb, t);
        else if (k === 'color') po[k] = lerpColor(pa[k] || vb, vb, t);
        else po[k] = vb;
      }
    });
    return out;
  }

  /* ---------------- 动画原语 ---------------- */

  var ANIM_TYPES = {
    /** 正弦漂移/呼吸/扫视 */
    sine: function (a, t) {
      return a.amp * Math.sin(TAU * t / (a.period || 2000) + (a.phase || 0));
    },
    /** 节奏缩放：0 → amp 平滑往复 */
    pulse: function (a, t) {
      return a.amp * 0.5 * (1 - Math.cos(TAU * t / (a.period || 1000) + (a.phase || 0)));
    },
    /** 随机小抖动（多正弦伪噪声），decay 毫秒内衰减到 0 */
    jitter: function (a, t, eng) {
      var s = t / 1000 * (a.speed || 8);
      var v = (Math.sin(s * 3.1 + eng._seed) +
               Math.sin(s * 5.7 + eng._seed * 2.3) +
               Math.sin(s * 9.3 + eng._seed * 4.1)) / 3 * a.amp;
      if (a.decay) v *= clamp(1 - t / a.decay, 0, 1);
      return v;
    },
    /** 三角波快速来回扫动 */
    scan: function (a, t) {
      var per = a.period || 800;
      var p = ((t + (a.phaseMs || 0)) % per) / per;
      var tri = p < 0.5 ? p * 4 - 1 : 3 - p * 4; /* -1 → 1 → -1 */
      return a.amp * tri;
    },
    /** 张望：平滑方波，在 ±amp 两端各停留片刻再换边（左看看、右看看） */
    glance: function (a, t) {
      var per = a.period || 3600;
      var ph = TAU * (((t + (a.phaseMs || 0)) % per) / per) + (a.phase || 0);
      return a.amp * Math.tanh(2.8 * Math.sin(ph));
    },
    /** 周期眨眼：interval 周期内前 dur 毫秒闭合再睁开（返回负值叠加到 open）
     *  相位叠加实例随机种子，多实例不会同步眨眼 */
    blink: function (a, t, eng) {
      var interval = a.interval || 3800, dur = a.dur || 200;
      var p = (t + (a.phaseMs || 0) + (eng ? eng._seed * 97 : 0)) % interval;
      if (p >= dur) return 0;
      return -(a.depth == null ? 1 : a.depth) * Math.sin(Math.PI * (p / dur));
    }
  };

  function applyAnim(pose, a, t, eng) {
    var fn = ANIM_TYPES[a.type];
    if (!fn) return;
    var v = fn(a, t, eng);
    var targets =
      a.target === 'eyes' ? [pose.left, pose.right] :
      a.target === 'body' ? [pose.body] :
      a.target === 'left' ? [pose.left] :
      a.target === 'right' ? [pose.right] : [];
    for (var i = 0; i < targets.length; i++) {
      var tg = targets[i];
      if (a.prop === 'scale') {
        if (tg === pose.body) tg.scale += v;
        else { tg.scaleX += v; tg.scaleY += v; }
      } else if (a.prop in tg) {
        tg[a.prop] += v;
      }
    }
  }

  /* ---------------- 配置注册中心 ---------------- */

  var GROUPS = (window.EMOTION_GROUPS || [
    { key: 'life', name: '生命周期' },
    { key: 'emotion', name: '情绪反应' },
    { key: 'agent', name: '代理工作状态' },
    { key: 'custom', name: '自定义' }
  ]).slice();

  var registry = new Map();
  var order = [];

  function knownGroup(g) {
    return GROUPS.some(function (x) { return x.key === g; });
  }

  function validate(raw) {
    var errs = [];
    if (!raw || typeof raw !== 'object') { errs.push('配置必须是对象'); return errs; }
    if (typeof raw.id !== 'string' || !raw.id.trim()) errs.push('缺少合法的字符串 id');
    if (typeof raw.name !== 'string' || !raw.name.trim()) errs.push('缺少 name');
    if (!knownGroup(raw.group)) errs.push('group 不合法：' + raw.group);
    if (raw.anims != null) {
      if (!Array.isArray(raw.anims)) errs.push('anims 必须是数组');
      else raw.anims.forEach(function (a, i) {
        if (!a || !ANIM_TYPES[a.type]) errs.push('anims[' + i + '] 未知动画类型：' + (a && a.type));
      });
    }
    if (raw.sequence != null && !Array.isArray(raw.sequence.frames)) {
      errs.push('sequence.frames 必须是数组');
    }
    return errs;
  }

  /** 归一化：深合并默认姿态，预生成 sequence 每帧的完整 pose */
  function normalize(raw) {
    var base = applySpec(defaultPose(), raw);
    // Ignore imported legacy indices: each known state has its own safe static silhouette.
    var pool = [window.EB_SAFE_FACE.indexOf(raw.id)];
    window.EB_SAFE_FACE.constrain(base);
    var def = {
      id: raw.id, name: raw.name, group: raw.group,
      desc: raw.desc || '',
      en: raw.en || null,   /* 可选英文文案 { name, desc } */
      gaze: raw.gaze !== false,
      transition: raw.transition != null ? raw.transition : 500,
      /* 表情池系统：pool = 眼环索引池，poolMs 间隔内随机轮换；
       * blinkMs = 眨眼间隔（null 不眨）；openness = 常驻开合度；
       * antics = 待机随机小动作（自旋 / 弹跳） */
      pool: pool,
      poolMs: raw.poolMs || [9000, 16000],
      poolSpeed: raw.poolSpeed || 6,
      blinkMs: raw.blinkMs !== undefined ? raw.blinkMs : [6000, 14000],
      openness: raw.openness != null ? raw.openness : 1,
      antics: !!raw.antics,
      base: base,
      anims: (raw.anims || []).map(function (a) { return Object.assign({}, a); }),
      sequence: null,
      raw: raw
    };
    if (raw.sequence) {
      var frames = raw.sequence.frames.map(function (f) {
        return { at: f.at || 0, pose: window.EB_SAFE_FACE.constrain(applySpec(clonePose(base), f)) };
      }).sort(function (x, y) { return x.at - y.at; });
      def.sequence = { frames: frames, settle: raw.sequence.settle || 'base' };
    }
    return def;
  }

  function register(raw) {
    var errs = validate(raw);
    if (errs.length) return { ok: false, id: raw && raw.id, errors: errs };
    var def = normalize(raw);
    if (!registry.has(def.id)) order.push(def.id);
    registry.set(def.id, def);
    return { ok: true, id: def.id };
  }

  EB.config = {
    register: register,
    get: function (id) { return registry.get(id) || null; },
    list: function (group) {
      return order.map(function (id) { return registry.get(id); })
        .filter(function (d) { return !group || d.group === group; });
    },
    groups: function () {
      return GROUPS.map(function (g) { return { key: g.key, name: g.name, en: g.en || g.name }; });
    },
    exportConfig: function () {
      return JSON.stringify(order.map(function (id) { return registry.get(id).raw; }), null, 2);
    },
    importConfig: function (json) {
      var data;
      try {
        data = typeof json === 'string' ? JSON.parse(json) : json;
      } catch (e) {
        return { ok: false, added: 0, errors: ['JSON 解析失败：' + e.message] };
      }
      var arr = Array.isArray(data) ? data : [data];
      var added = 0, errors = [];
      arr.forEach(function (raw) {
        var r = register(raw);
        if (r.ok) added++;
        else errors.push('[' + ((raw && raw.id) || '?') + '] ' + r.errors.join('；'));
      });
      return { ok: errors.length === 0, added: added, errors: errors };
    }
  };

  /* ---------------- 全局共享 rAF 时钟（多实例单循环） ---------------- */

  var ticker = {
    set: new Set(),
    raf: 0,
    add: function (e) {
      this.set.add(e);
      if (!this.raf) this.raf = requestAnimationFrame(ticker.loop);
    },
    remove: function (e) { this.set.delete(e); },
    loop: function (now) {
      ticker.raf = 0;
      ticker.set.forEach(function (e) { e._tick(now); });
      if (ticker.set.size) ticker.raf = requestAnimationFrame(ticker.loop);
    }
  };

  /* ---------------- EmotionEngine ---------------- */

  function Engine(target, opts) {
    opts = opts || {};
    var el = typeof target === 'string' ? document.querySelector(target) : target;
    if (!el) throw new Error('EmotionBall.create：找不到容器元素');

    this.ball = EB.createBall(el, Object.assign({}, opts, {
      lite: opts.lite != null ? opts.lite : opts.autostart === false
    }));
    this._seed = Math.random() * 100;
    this._events = {};
    this._gaze = { x: 0, y: 0, tx: 0, ty: 0 };
    this._style = { sketch: 0 };
    this._theme = opts.color
      ? { body: opts.color, eyes: opts.eyeColor || '#FFFFFF' }
      : null;
    this._eyeScale = opts.eyeScale || 1;
    this._lastTick = 0;
    this._spin = null;   /* { x, v, t }：弹簧驱动的整圈自旋（彩带触发源） */

    /* ---- 表情形变系统 ---- */
    this._ringSrc = [EXPR[0][0], EXPR[0][1]];   /* 形变起点环对 */
    this._ringDst = [EXPR[0][0], EXPR[0][1]];   /* 形变目标环对 */
    this._ringCur = this._ringDst;              /* 当前展示环对（引用比较驱动 d 更新） */
    this._ringSpring = spring(1);               /* 形变进度 0→1 */
    this._ringSpeed = 7;
    this._exprIdx = 0;
    this._poolPos = 0;
    this._poolNext = 0;
    /* ---- 眨眼系统：开合度弹簧（频率 26）+ 关键帧队列 ---- */
    this._open = spring(1);
    this._blinkQ = [];
    this._blinkNext = Infinity;
    /* ---- 待机小动作 ---- */
    this._anticNext = 0;
    this._bounceAt = -1;

    this._def = null;
    this._lastPose = null;
    this._prevPose = null;
    this._transStart = 0;
    this._transDur = 0;
    this._emoStart = 0;
    this._seq = null;
    this._active = false;
    this._touring = false;
    this._tourTimer = 0;
    this._fallbackId = opts.fallbackId || FALLBACK_ID;
    this._lastActivity = performance.now();

    if (opts.idle) {
      this._idle = Object.assign(
        { standbyAfter: 60000, sleepAfter: 180000, standbyId: '02', sleepId: '00' },
        opts.idle === true ? {} : opts.idle
      );
    } else {
      this._idle = null;
    }

    this.setEmotion(opts.emotion || this._fallbackId, { auto: true });
    if (opts.autostart !== false) this.setActive(true);
    else this.renderStatic();
  }

  Engine.prototype = {

    /* ---------- 事件 ---------- */
    on: function (evt, cb) {
      (this._events[evt] = this._events[evt] || []).push(cb);
      return this;
    },
    off: function (evt, cb) {
      var list = this._events[evt];
      if (list) {
        var i = list.indexOf(cb);
        if (i >= 0) list.splice(i, 1);
      }
      return this;
    },
    _emit: function (evt, payload) {
      (this._events[evt] || []).slice().forEach(function (cb) {
        try { cb(payload); } catch (e) { console.error(e); }
      });
    },

    get emotionId() { return this._def ? this._def.id : null; },
    get touring() { return this._touring; },

    /* ---------- 核心：切换表情（含兜底） ---------- */
    setEmotion: function (id, o) {
      o = o || {};
      var def = EB.config.get(id);
      if (!def) {
        console.warn('[EmotionBall] 未知表情 ID "' + id + '"，回退到待机 (' + this._fallbackId + ')');
        this._emit('error', { message: '未知表情 ID "' + id + '"，已回退待机', id: id });
        def = EB.config.get(this._fallbackId);
        if (!def) return false;
      }
      var now = performance.now();
      var prevId = this._def ? this._def.id : null;
      this._prevPose = this._lastPose ? clonePose(this._lastPose) : null;
      this._def = def;
      this._emoStart = now;
      this._transStart = now;
      this._transDur = this._prevPose ? def.transition : 0;
      this._seq = def.sequence
        ? { frames: def.sequence.frames, settle: def.sequence.settle, done: false }
        : null;
      if (!o.auto) this._lastActivity = now;

      /* 状态切换：眼环弹到新池首个表情（兴奋类用更快弹簧），
       * 并且切换瞬间先眨一次眼（睡眠 / 停止类除外） */
      this._poolPos = 0;
      this._setExpr(def.pool[0], def.poolSpeed >= 10 ? 10 : 8);
      this._poolNext = now + rand(def.poolMs[0], def.poolMs[1]);
      if (prevId !== null && prevId !== def.id && def.blinkMs) this._blinkNow(now);
      this._blinkNext = def.blinkMs ? now + rand(def.blinkMs[0], def.blinkMs[1]) : Infinity;
      this._anticNext = now + rand(2500, 5000);

      this._emit('change', { id: def.id, def: def, auto: !!o.auto });
      /* 配置中的 ribbons / confetti 是进入表情时的一次性事件：
       * ribbons → 自旋甩彩带；confetti → 撒花爆发 */
      if (this._active) {
        var fx = def.base.body;
        if (fx.ribbons > 0) this.spin(fx.ribbons >= 1 ? 2 : 1);
        if (fx.confetti > 0) this.burst(20);
      }
      if (!this._active) this.renderStatic();
      return true;
    },

    /** AI 对接入口：接受对象或 JSON 字符串 { emotionId, tips } */
    handleAIMessage: function (msg) {
      var obj = msg;
      if (typeof msg === 'string') {
        try { obj = JSON.parse(msg); }
        catch (e) {
          this._emit('error', { message: 'AI 消息 JSON 解析失败，已回退待机', raw: msg });
          this.setEmotion(this._fallbackId);
          return false;
        }
      }
      if (!obj || typeof obj !== 'object' || typeof obj.emotionId !== 'string') {
        this._emit('error', { message: 'AI 消息缺少 emotionId 字段，已回退待机', raw: msg });
        this.setEmotion(this._fallbackId);
        return false;
      }
      var ok = this.setEmotion(obj.emotionId);
      if (obj.tips) this._emit('tips', { text: String(obj.tips) });
      return ok;
    },

    /* ---------- 自动巡演 ---------- */
    startTour: function (ids, interval) {
      this.stopTour();
      if (!ids || !ids.length) return;
      interval = interval || 2500;
      this._touring = true;
      var self = this, i = 0;
      this.setEmotion(ids[0], { auto: true });
      this._tourTimer = setInterval(function () {
        i = (i + 1) % ids.length;
        self.setEmotion(ids[i], { auto: true });
      }, interval);
    },
    stopTour: function () {
      if (this._tourTimer) { clearInterval(this._tourTimer); this._tourTimer = 0; }
      this._touring = false;
      this._lastActivity = performance.now();
    },

    resetIdle: function () { this._lastActivity = performance.now(); },

    /* 注视目标：横向 ±24、纵向 ±15（viewBox 坐标），幅度克制以保持含蓄 */
    setGaze: function (nx, ny) {
      this._gaze.tx = clamp(nx, -1, 1) * 24;
      this._gaze.ty = clamp(ny, -1, 1) * 15;
      return this;
    },
    clearGaze: function () {
      this._gaze.tx = 0;
      this._gaze.ty = 0;
      return this;
    },
    setStyle: function (style) {
      Object.assign(this._style, style || {});
      if (!this._active) this.renderStatic();
      return this;
    },

    /* 自旋（点击交互）：弹簧追整数圈，达速后由渲染层甩出彩带；
     * 进行中的自旋不可打断，追加请求直接忽略 */
    spin: function (turns, dir) {
      if (this._spin) return this;
      var d = dir || (Math.random() < 0.5 ? -1 : 1);
      this._spin = { x: 0, v: 0, t: Math.max(1, Math.round(turns || 1)) * TAU * d };
      return this;
    },
    /* 撒花：一次性物理粒子爆发 */
    burst: function (count) {
      if (this.ball.burst) this.ball.burst(count);
      return this;
    },
    /* 弹跳（4 段递减抛物线） */
    bounce: function () {
      if (this._bounceAt < 0) this._bounceAt = performance.now();
      return this;
    },

    /* 切换眼环目标：把当前插值冻结为新起点，弹簧从 0 重新弹向 1 */
    _setExpr: function (idx, speed) {
      if (idx === this._exprIdx && this._ringSpring.x >= 0.999) return;
      var s = clamp(this._ringSpring.x, 0, 1);
      this._ringSrc = [
        lerpRing(this._ringSrc[0], this._ringDst[0], s),
        lerpRing(this._ringSrc[1], this._ringDst[1], s)
      ];
      this._ringDst = [EXPR[idx][0], EXPR[idx][1]];
      this._ringSpring.x = 0;
      this._ringSpring.v = 0;
      this._ringSpring.t = 1;
      this._ringSpeed = speed || 7;
      this._exprIdx = idx;
    },

    /* 眨眼关键帧：合上 → 停 70ms → 睁到 1.08 过冲 → 300ms 落回 1，
     * 14% 概率追加第二次连眨 */
    _blinkNow: function (t) {
      this._blinkQ.push(
        { at: t, v: 0.05 }, { at: t + 70, v: 0.05 },
        { at: t + 150, v: 1.08 }, { at: t + 300, v: 1 }
      );
      if (Math.random() < 0.14) {
        this._blinkQ.push({ at: t + 370, v: 0.05 }, { at: t + 480, v: 1 });
      }
    },

    registerEmotion: function (raw) { return EB.config.register(raw); },

    /* ---------- 生命周期 ---------- */
    setActive: function (on) {
      if (on === this._active) return;
      this._active = on;
      if (on) ticker.add(this);
      else ticker.remove(this);
    },
    replay: function () {
      if (this._def) this.setEmotion(this._def.id, { auto: true });
    },
    /** 静态渲染一帧（缩略图用基础姿态，不播 sequence 第 0 帧；弹簧直接置终值） */
    renderStatic: function () {
      this._transDur = 0;
      this._ringSpring.x = 1;
      this._ringSpring.v = 0;
      this._open.x = this._def ? this._def.openness : 1;
      this._open.v = 0;
      var seq = this._seq;
      this._seq = null;
      this._tick(performance.now());
      this._seq = seq;
    },
    destroy: function () {
      this.stopTour();
      this.setActive(false);
      this._events = {};
      this.ball.destroy();
    },

    /* ---------- 每帧 ---------- */
    _tick: function (now) {
      this._dt = this._lastTick ? clamp((now - this._lastTick) / 1000, 0.001, 0.05) : 1 / 60;
      this._lastTick = now;
      if (this._idle && !this._touring) this._checkIdle(now);
      var pose = this._compose(now, 0);
      this.ball.applyPose(pose);
      this._lastPose = pose;
    },

    _checkIdle: function (now) {
      var idle = this._idle;
      var elapsed = now - this._lastActivity;
      var cur = this.emotionId;
      if (elapsed >= idle.sleepAfter) {
        if (cur !== idle.sleepId) this.setEmotion(idle.sleepId, { auto: true });
      } else if (elapsed >= idle.standbyAfter) {
        if (cur !== idle.standbyId && cur !== idle.sleepId) {
          this.setEmotion(idle.standbyId, { auto: true });
        }
      }
    },

    /** 合成当前帧姿态：base → sequence → animators → 过渡插值 */
    _compose: function (now, depth) {
      var def = this._def;
      var t = now - this._emoStart;
      var pose;

      if (this._seq) {
        var res = this._seqPose(t, now);
        if (res === 'switch') {
          /* sequence 播完且 settle.next：已切到新表情，重新合成 */
          return depth < 4 ? this._compose(now, depth + 1) : clonePose(this._def.base);
        }
        pose = res || clonePose(def.base);
      } else {
        pose = clonePose(def.base);
      }

      /* 内置呼吸（相位用绝对时间，切换表情不跳变） */
      var br = pose.body.breathe || 0;
      if (br) {
        var ph = TAU * now / 3600;
        pose.body.scale += br * Math.sin(ph);
        pose.body.y += br * 55 * Math.sin(ph + 0.6);
      }

      for (var i = 0; i < def.anims.length; i++) applyAnim(pose, def.anims[i], t, this);

      pose.body.sketch = Math.max(pose.body.sketch || 0, this._style.sketch || 0);

      var dt = this._dt || 1 / 60;

      /* ---- 表情池轮换：poolMs 间隔内随机跳到池内另一个眼环 ---- */
      if (this._active && now >= this._poolNext) {
        if (def.pool.length > 1) {
          this._poolPos = (this._poolPos + 1 + Math.floor(rand(0, def.pool.length - 1))) % def.pool.length;
          this._setExpr(def.pool[this._poolPos], def.poolSpeed);
        }
        this._poolNext = now + rand(def.poolMs[0], def.poolMs[1]);
      }

      /* ---- 眨眼调度：间隔到点入队关键帧，队列驱动开合度弹簧目标 ---- */
      if (this._active && def.blinkMs && now >= this._blinkNext) {
        this._blinkNow(now);
        this._blinkNext = now + rand(def.blinkMs[0], def.blinkMs[1]);
      }
      var openKey = null;
      while (this._blinkQ.length && now >= this._blinkQ[0].at) {
        openKey = this._blinkQ[0].v;
        this._blinkQ.shift();
      }
      this._open.t = openKey != null ? openKey : (this._blinkQ.length ? this._open.t : def.openness);

      /* ---- 待机小动作：9~18s 随机自旋 / 弹跳 ---- */
      if (this._active && def.antics && now >= this._anticNext) {
        if (!this._spin && this._bounceAt < 0) {
          var pick = Math.random();
          if (pick < 0.45) this.spin(1);
          else if (pick < 0.8) this.bounce();
          else this._blinkNow(now);
        }
        this._anticNext = now + rand(9000, 18000);
      }

      /* ---- 弹簧整步（子步 1/120 保稳定）：形变 / 开合 / 自旋 ---- */
      var steps = Math.max(1, Math.ceil(dt / (1 / 120)));
      var j = dt / steps;
      for (var si = 0; si < steps; si++) {
        springStep(this._ringSpring, this._ringSpeed, 1, j);
        springStep(this._open, 26, 1, j);
        if (this._spin) {
          springStep(this._spin, 6.2, 1, j);
          if (Math.abs(this._spin.t - this._spin.x) < 0.01 && Math.abs(this._spin.v) < 0.05) {
            this._spin = null;
          }
        }
      }
      pose.body.yaw = this._spin ? this._spin.x : 0;

      /* ---- 弹跳位移：-4·h·n(1-n) 抛物线 ---- */
      if (this._bounceAt >= 0) {
        var be = (now - this._bounceAt) / 1000;
        if (be >= BOUNCE_TOTAL) {
          this._bounceAt = -1;
        } else {
          var acc = 0, bi = 0;
          while (bi < BOUNCE_SEGS.length && be >= acc + BOUNCE_SEGS[bi].d) { acc += BOUNCE_SEGS[bi].d; bi++; }
          var seg = BOUNCE_SEGS[Math.min(bi, BOUNCE_SEGS.length - 1)];
          var bn = (be - acc) / seg.d;
          pose.body.y += -4 * seg.h * bn * (1 - bn);
        }
      }

      /* ---- 当前眼环：形变中逐点插值，静止后复用目标引用（跳过 d 重建） ---- */
      if (this._ringSpring.x < 0.999 || this._ringSpring.v > 0.001 || this._ringSpring.v < -0.001) {
        var rs = clamp(this._ringSpring.x, 0, 1.35);
        this._ringCur = [
          lerpRing(this._ringSrc[0], this._ringDst[0], rs),
          lerpRing(this._ringSrc[1], this._ringDst[1], rs)
        ];
      } else if (this._ringCur !== this._ringDst) {
        this._ringCur = this._ringDst;
      }
      pose.left.ring = this._ringCur[0];
      pose.right.ring = this._ringCur[1];

      /* 鼠标注视：帧率无关的指数平滑（60fps 基准下每帧收敛约 9%） */
      var k = 1 - Math.exp(-5.66 * dt);
      var gx = def.gaze !== false ? this._gaze.tx : 0;
      var gy = def.gaze !== false ? this._gaze.ty : 0;
      this._gaze.x += (gx - this._gaze.x) * k;
      this._gaze.y += (gy - this._gaze.y) * k;
      pose.left.lookX += this._gaze.x;
      pose.right.lookX += this._gaze.x;
      pose.left.lookY += this._gaze.y;
      pose.right.lookY += this._gaze.y;

      /* 常驻眼神微漂移：每只眼相位错开，永不完全静止 */
      if (def.gaze !== false) {
        var w = now / 1000;
        pose.left.lookX += 1.4 * Math.sin(0.42 * w) + 0.5 * Math.sin(1.0 * w);
        pose.right.lookX += 1.4 * Math.sin(0.42 * w + 1) + 0.5 * Math.sin(1.0 * w + 2);
        pose.left.lookY += 0.9 * Math.sin(0.58 * w);
        pose.right.lookY += 0.9 * Math.sin(0.58 * w + 1);
      }

      /* 小尺寸实例放大眼睛占比，保证 32~48px 下仍可读 */
      if (this._eyeScale !== 1) {
        pose.left.scaleX *= this._eyeScale;
        pose.left.scaleY *= this._eyeScale;
        pose.right.scaleX *= this._eyeScale;
        pose.right.scaleY *= this._eyeScale;
      }

      /* 实例主题色（baby bot）：体色恒为主题色，眼睛仅覆盖默认黑 */
      if (this._theme) {
        pose.body.color = this._theme.body;
        if (pose.left.color === DEFAULT_EYE.color) pose.left.color = this._theme.eyes;
        if (pose.right.color === DEFAULT_EYE.color) pose.right.color = this._theme.eyes;
      }

      /* 开合度 = 配置基础值 × 眨眼弹簧（弹簧可过冲到 1.08） */
      var openS = clamp(this._open.x, 0.02, 1.5);
      pose.left.open = clamp(pose.left.open, 0, 1.3) * openS;
      pose.right.open = clamp(pose.right.open, 0, 1.3) * openS;
      pose.left.scaleX = Math.max(pose.left.scaleX, 0.05);
      pose.left.scaleY = Math.max(pose.left.scaleY, 0.05);
      pose.right.scaleX = Math.max(pose.right.scaleX, 0.05);
      pose.right.scaleY = Math.max(pose.right.scaleY, 0.05);

      /* 表情切换过渡插值 */
      var tt = now - this._transStart;
      if (this._transDur > 0 && tt < this._transDur && this._prevPose) {
        pose = lerpPose(this._prevPose, pose, easeInOutCubic(tt / this._transDur));
      }
      return window.EB_SAFE_FACE.constrain(pose);
    },

    /** sequence 采样；播完按 settle 处理（hold / base / next） */
    _seqPose: function (t, now) {
      var seq = this._seq;
      var frames = seq.frames;
      var last = frames[frames.length - 1];

      if (t >= last.at) {
        if (!seq.done) {
          seq.done = true;
          var s = seq.settle;
          if (s === 'base') {
            /* 从序列末帧平滑回落到基础姿态 */
            this._prevPose = this._lastPose ? clonePose(this._lastPose) : clonePose(last.pose);
            this._transStart = now;
            this._transDur = this._def.transition || 500;
            this._seq = null;
            return null;
          }
          if (s && typeof s === 'object' && s.next) {
            this.setEmotion(s.next, { auto: true });
            return 'switch';
          }
          /* settle === 'hold'：定格在末帧 */
        }
        return clonePose(last.pose);
      }

      if (t <= frames[0].at) return clonePose(frames[0].pose);
      for (var i = 0; i < frames.length - 1; i++) {
        var a = frames[i], b = frames[i + 1];
        if (t >= a.at && t < b.at) {
          var k = easeInOutCubic((t - a.at) / (b.at - a.at));
          return lerpPose(a.pose, b.pose, k);
        }
      }
      return clonePose(last.pose);
    }
  };

  /* ---------------- 对外入口 ---------------- */

  EB.create = function (target, opts) { return new Engine(target, opts); };
  EB.version = '1.0.0';

  /* 载入种子配置（emotions.js 在本脚本之前加载） */
  if (Array.isArray(window.EMOTION_SEED)) {
    window.EMOTION_SEED.forEach(function (raw) {
      var r = register(raw);
      if (!r.ok) console.warn('[EmotionBall] 种子配置无效：', r.id, r.errors);
    });
  }
})();

/* GrokBall compatibility facade: keep the public API and panel data stable. */
(function () {
  'use strict';
  var EB = window.EmotionBall;
  var groupKeys = { life: true, emotion: true, agent: true };
  var groups = (window.EMOTION_GROUPS || []).filter(function (group) {
    return !!groupKeys[group.key];
  });
  var emotions = (window.EMOTION_SEED || []).map(function (emotion) {
    return {
      id: emotion.id,
      group: emotion.group,
      name: emotion.name,
      desc: emotion.desc || '',
      color: emotion.body && emotion.body.color || '#F3F0EA'
    };
  });

  window.GrokBall = {
    create: EB.create,
    EMOTIONS: emotions,
    GROUPS: groups,
    config: EB.config,
    version: EB.version
  };
})();

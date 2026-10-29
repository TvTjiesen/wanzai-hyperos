# -*- coding: utf-8 -*-
"""�?verify/b4-12/pages/base/engine_dom12.html 里的十二个表情球矢量烘成 Kotlin 常量�?
为什么必须机器烘：眼睛路径上有两�?transform（外�?<g> + 每条 path 自己�?translate/scale/rotate），
手抄必错。这里把两层仿射乘完，坐标归一化到「球体直�?= 1.0、圆�?= (0,0)」，
运行时只乘直径再平移，不做矩阵�?
可复跑：PYTHONIOENCODING=utf-8 python tools/gen_ai_ball_glyphs.py
复核：verify/b4-12/check_ball_source.py 重新解析 HTML 并与 Kotlin 常量逐点比对（差 > 1e-5 即红）�?"""
import hashlib, io, math, os, re, sys

BASE = '[local]'
SRC = BASE + '/verify/b4-12/pages/base/engine_dom12b.html'
DST = BASE + '/app/src/main/java/com/muyao/wanzaihyperos/ui/design/ai/AiBallGlyphs.kt'

EMOTIONS = [
    ('00', 'Sleep'),
    ('01', 'WakeUp'),
    ('02', 'Idle'),
    ('03', 'Curious'),
    ('16', 'Focus'),
    ('30', 'Thinking'),
    ('32', 'Busy'),
    ('33', 'Done'),
    ('18', 'Error'),
    ('35', 'Waiting'),
    ('36', 'Loading'),
    ('40', 'Searching'),
]

# 引擎 svg viewBox="-15 -15 259 259"，球体路�?bbox 0..228.54 / 0.01..228.44�?# 归一化基准用球体 bbox（不�?viewBox）：中心 (114.27, 114.225)，直�?228.54�?CX, CY, DIAM = 114.27, 114.225, 228.54


def mul(m1, m2):
    a1, b1, c1, d1, e1, f1 = m1
    a2, b2, c2, d2, e2, f2 = m2
    return (a1 * a2 + c1 * b2,
            b1 * a2 + d1 * b2,
            a1 * c2 + c1 * d2,
            b1 * c2 + d1 * d2,
            a1 * e2 + c1 * f2 + e1,
            b1 * e2 + d1 * f2 + f1)


def ident():
    return (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)


def parse_transform(s):
    m = ident()
    for name, args in re.findall(r'([a-zA-Z]+)\s*\(([^)]*)\)', s or ''):
        v = [float(t) for t in re.split(r'[ ,]+', args.strip()) if t]
        if name == 'translate':
            tx = v[0]
            ty = v[1] if len(v) > 1 else 0.0
            m = mul(m, (1.0, 0.0, 0.0, 1.0, tx, ty))
        elif name == 'scale':
            sx = v[0]
            sy = v[1] if len(v) > 1 else v[0]
            m = mul(m, (sx, 0.0, 0.0, sy, 0.0, 0.0))
        elif name == 'rotate':
            ang = math.radians(v[0])
            ca, sa = math.cos(ang), math.sin(ang)
            rot = (ca, sa, -sa, ca, 0.0, 0.0)
            if len(v) >= 3:
                m = mul(m, (1.0, 0.0, 0.0, 1.0, v[1], v[2]))
                m = mul(m, rot)
                m = mul(m, (1.0, 0.0, 0.0, 1.0, -v[1], -v[2]))
            else:
                m = mul(m, rot)
        elif name == 'matrix':
            m = mul(m, tuple(v) + (0.0,) * (6 - len(v)))
        else:
            raise SystemExit('未支持的 transform: ' + name)
    return m


def apply(m, p):
    a, b, c, d, e, f = m
    return (a * p[0] + c * p[1] + e, b * p[0] + d * p[1] + f)


NUM = re.compile(r'[-+]?(?:\d+\.\d+|\.\d+|\d+)(?:[eE][-+]?\d+)?')
CMD = re.compile(r'([MLZmlzHhVvCcSsQqTtAa])')


def path_points(d):
    letters = set(CMD.findall(d))
    if not letters <= set('MLZ'):
        raise SystemExit('路径里出现了�?M/L/Z 指令: ' + str(sorted(letters)))
    nums = [float(t) for t in NUM.findall(d)]
    if len(nums) % 2:
        raise SystemExit('坐标数不是偶�? %d' % len(nums))
    return [(nums[i], nums[i + 1]) for i in range(0, len(nums), 2)]


def main():
    html = io.open(SRC, encoding='utf-8').read()
    svgs = re.findall(r'<svg\b[^>]*>(.*?)</svg>', html, re.S)
    if len(svgs) != len(EMOTIONS):
        raise SystemExit('svg 数不�?%d: %d' % (len(EMOTIONS), len(svgs)))

    blocks = []
    report = []
    for idx, (emoid, kotname) in enumerate(EMOTIONS):
        body = svgs[idx]
        g = re.search(r'<g\b[^>]*\btransform="([^"]*)"', body)
        if not g:
            raise SystemExit('�?%d �?svg 没有 <g transform>' % idx)
        gm = parse_transform(g.group(1))
        eyes = []
        for tag in re.findall(r'<path\b[^>]*>', body):
            fill = re.search(r'\bfill="([^"]*)"', tag).group(1)
            if fill.startswith('url(#'):
                continue
            d = re.search(r'\bd="([^"]*)"', tag).group(1)
            tm = re.search(r'\btransform="([^"]*)"', tag)
            pm = parse_transform(tm.group(1) if tm else '')
            eyes.append((mul(gm, pm), path_points(d)))
        if len(eyes) != 2:
            raise SystemExit('�?%d �?svg 眼睛数不�?2: %d' % (idx, len(eyes)))

        # �?每只眼睛单独一段，**不许压成一条平铺数�?*�?        #   眼睛是多边形（一只眼 ~48 个顶点），压平之后两个多边形之间没有分隔�?        #   运行时只能靠「每 4 个数一组的四边形」去�?�?画出的是交叉斜线�?        #   �?026-10-25 模拟器帧上就是这么一坨，�?[local]/b412/live/zoom_tr.png）�?        eye_flats = []
        xs = []
        ys = []
        for pm, pts in eyes:
            flat = []
            for p in pts:
                q = apply(pm, p)
                nx = (q[0] - CX) / DIAM
                ny = (q[1] - CY) / DIAM
                flat.append(nx)
                flat.append(ny)
                xs.append(nx)
                ys.append(ny)
            eye_flats.append(flat)
        blocks.append((emoid, kotname, eye_flats))
        report.append((emoid, kotname, sum(len(f) // 2 for f in eye_flats), min(xs), max(xs), min(ys), max(ys),
                       math.sqrt(max(x * x + y * y for x, y in zip(xs, ys)))))

    for r in report:
        print('%-3s %-8s pts=%3d  x[%+.4f..%+.4f] y[%+.4f..%+.4f]  maxR=%.4f'
              % (r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7]))

    w = []
    p = w.append
    p('package com.muyao.wanzaihyperos.ui.design.ai')
    p('')
    p('/**')
    p(' * 十二个表情的眼睛多边�?—�?**机器烘出来的，不要手�?*�?)
    p(' *')
    p(' * 来源：engine_dom.html（verify/b4-12/pages/base/）—�?引擎原始 DOM�?2 �?svg x 3 �?path�?)
    p(' * 生成：PYTHONIOENCODING=utf-8 python tools/gen_ai_ball_glyphs.py')
    p(' * 复核：verify/b4-12/check_ball_source.py 重新解析 HTML，与本文件逐点比对（差 > 1e-5 即红）�?)
    p(' *')
    p(' * 坐标已归一化：球心 = (0, 0)�?*球直�?= 1.0**，于是运行时只乘直径再平移�?)
    p(' * 两层 transform（外�?g + 每条 path 自己的）已经乘完 —�?只取外层会得到一张歪脸�?)
    p(' *')
    p(' * 球体本身不在这里：它是一�?96 点正圆（径向偏差 max 0.0675 单位 = 0.034dp），')
    p(' * 直接 drawCircle 即可，不必复刻那 1229 个字符�?)
    p(' */')
    p('internal object AiBallGlyphs {')
    p('')
    p('    /** 眼睛填充色（引擎 fill="#111516"，十二个表情一致）�?*/')
    p('    const val EYE_COLOR = 0xFF111516')
    p('')
    p('    /** 球体径向渐变：radialGradient cx=38% cy=32% r=75%，三�?0% / 62% / 100%�?*/')
    p('    val BODY_STOPS = floatArrayOf(0.00f, 0.62f, 1.00f)')
    p('    val BODY_COLORS = intArrayOf(0xFFF6F3EE.toInt(), 0xFFF3F0E9.toInt(), 0xFFD6D3CD.toInt())')
    p('    const val GRADIENT_CX = 0.38f')
    p('    const val GRADIENT_CY = 0.32f')
    p('    const val GRADIENT_R = 0.75f')
    p('')
    for emoid, kotname, eye_flats in blocks:
        p('    /**')
        p('     * 表情 %s -> %s�?*每只眼睛一条数�?*：一只眼是一个多边形（~48 个顶点）�? % (emoid, kotname))
        p('     * 两个多边形之间有明确分隔 —�?压成一条会被四边形猜法画成交叉斜线团�?)
        p('     */')
        p('    val %s = listOf(' % kotname.upper())
        for flat in eye_flats:
            p('        floatArrayOf(')
            for i in range(0, len(flat), 8):
                p('            ' + ', '.join('%.6ff' % v for v in flat[i:i + 8]) + ',')
            p('        ),')
        p('    )')
        p('')
    p('    /** 按状态取眼睛：外层是眼睛，内层是该眼睛的顶点 [x0,y0,x1,y1,...]�?*/')
    p('    fun eyesOf(state: AiBallState): List<FloatArray> = when (state) {')
    for emoid, kotname, eye_flats in blocks:
        p('        AiBallState.%s -> %s' % (kotname, kotname.upper()))
    p('    }')
    p('}')
    p('')

    d = os.path.dirname(DST)
    if not os.path.isdir(d):
        os.makedirs(d)
    with io.open(DST, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(w))
    sz = os.path.getsize(DST)
    md5 = hashlib.md5(open(DST, 'rb').read()).hexdigest()
    print('WROTE %s %d B %s' % (DST, sz, md5))


main()

/**
 * canvas.js — 3D animated particle background.
 * Draws a network of floating nodes with animated "bus route" tracers.
 * Self-contained; call Canvas.init() on DOMContentLoaded.
 */

const Canvas = (() => {

  function init() {
    const canvas = document.getElementById('hero-canvas');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    let W, H, particles = [];
    let t = 0;

    // ---- Resize handler ----
    function resize() {
      W = canvas.width  = window.innerWidth;
      H = canvas.height = window.innerHeight;
    }
    resize();
    window.addEventListener('resize', resize);

    // ---- Seed particles ----
    for (let i = 0; i < 85; i++) {
      particles.push({
        x:  Math.random() * 1400,
        y:  Math.random() * 900,
        z:  Math.random() * 400 + 100,
        vx: (Math.random() - 0.5) * 0.28,
        vy: (Math.random() - 0.5) * 0.28,
        r:  Math.random() * 1.8 + 0.4,
        warm: Math.random() > 0.5    // accent vs accent2 colour
      });
    }

    // Route tracers — animated dots moving along virtual bus paths
    const routes = [
      { x1: 0.05, y1: 0.25, x2: 0.95, y2: 0.75, col: '108,99,255' },
      { x1: 0.15, y1: 0.85, x2: 0.85, y2: 0.15, col: '255,107,107' },
      { x1: 0.00, y1: 0.50, x2: 1.00, y2: 0.50, col: '67,233,123'  },
      { x1: 0.50, y1: 0.00, x2: 0.50, y2: 1.00, col: '108,99,255'  },
    ];

    // ---- Draw loop ----
    function draw() {
      ctx.clearRect(0, 0, W, H);
      t += 0.004;

      // Subtle vertical grid lines that sway
      for (let i = 0; i < 9; i++) {
        const x = (i / 8) * W;
        ctx.beginPath();
        ctx.moveTo(x + Math.sin(t + i * 0.6) * 18, 0);
        ctx.lineTo(x + Math.sin(t + i * 0.6 + Math.PI) * 18, H);
        ctx.strokeStyle = 'rgba(108,99,255,0.03)';
        ctx.lineWidth = 1;
        ctx.stroke();
      }

      // Particle nodes + connection lines
      for (let i = 0; i < particles.length; i++) {
        const p = particles[i];
        p.x += p.vx;
        p.y += p.vy;
        if (p.x < 0) p.x = W;
        if (p.x > W) p.x = 0;
        if (p.y < 0) p.y = H;
        if (p.y > H) p.y = 0;

        const depth = 1 - p.z / 500;
        const size  = p.r * depth * 2 + 0.5;
        const col   = p.warm ? '108,99,255' : '255,107,107';

        ctx.beginPath();
        ctx.arc(p.x, p.y, size, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(${col},${depth * 0.65})`;
        ctx.fill();

        // Connect nearby particles
        for (let j = i + 1; j < particles.length; j++) {
          const q = particles[j];
          const dx = q.x - p.x, dy = q.y - p.y;
          const dist = Math.sqrt(dx * dx + dy * dy);
          if (dist < 115) {
            ctx.beginPath();
            ctx.moveTo(p.x, p.y);
            ctx.lineTo(q.x, q.y);
            ctx.strokeStyle = `rgba(108,99,255,${(1 - dist / 115) * 0.1})`;
            ctx.lineWidth = 0.5;
            ctx.stroke();
          }
        }
      }

      // Route tracer dots with trailing glow
      routes.forEach((r, idx) => {
        const progress = ((t * 0.25) + idx * 0.25) % 1;
        const cx = (r.x1 + (r.x2 - r.x1) * progress) * W;
        const cy = (r.y1 + (r.y2 - r.y1) * progress) * H;

        // Head
        ctx.beginPath();
        ctx.arc(cx, cy, 4.5, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(${r.col},0.55)`;
        ctx.fill();

        // Tail
        for (let k = 1; k <= 7; k++) {
          const tp = Math.max(0, progress - k * 0.013);
          const tx = (r.x1 + (r.x2 - r.x1) * tp) * W;
          const ty = (r.y1 + (r.y2 - r.y1) * tp) * H;
          ctx.beginPath();
          ctx.arc(tx, ty, 4.5 - k * 0.55, 0, Math.PI * 2);
          ctx.fillStyle = `rgba(${r.col},${0.3 / k})`;
          ctx.fill();
        }
      });

      requestAnimationFrame(draw);
    }

    draw();
  }

  return { init };

})();

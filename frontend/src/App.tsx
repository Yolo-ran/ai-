import * as React from 'react';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import { Settings2 } from 'lucide-react';

type JavaLobbyBridge = {
  onWebReady?: () => void;
  selectGame?: (index: number) => void;
  navigate?: (direction: number) => void;
  launchSelected?: () => void;
  openApiSettings?: () => void;
};

type LobbyWebApi = {
  setActive: (index: number) => void;
  setConfirmProgress: (progress: number) => void;
  setHand: (visible: boolean, x: number, y: number) => void;
  setGesture: (visible: boolean, x: number, y: number, progress: number) => void;
  setCameraFrame: (frame: string) => void;
  activate: () => void;
  pause: () => void;
};

declare global {
  interface Window {
    javaLobby?: JavaLobbyBridge;
    gestureLobby?: LobbyWebApi;
  }
}

type ParticleAnimationProps = {
  particleCount?: number;
  colors?: string[];
  animationDuration?: [number, number];
};

const random = (min: number, max: number) => Math.random() * (max - min) + min;
const STAR_COLORS = ['#fff200', '#a855f7', '#f43f5e', '#22c55e'];

const ParticleAnimation = React.memo(function ParticleAnimation({
  particleCount = 340,
  colors = STAR_COLORS,
  animationDuration = [1, 5],
}: ParticleAnimationProps) {
  const canvasRef = React.useRef<HTMLCanvasElement>(null);

  React.useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const context = canvas.getContext('2d', { alpha: false });
    if (!context) return;
    const sprites = new Map<string, HTMLCanvasElement>();
    const particles = Array.from({ length: particleCount }, () => {
      const pitch = random(-Math.PI, Math.PI);
      return {
        angle: random(0, Math.PI * 2),
        duration: random(animationDuration[0], animationDuration[1]),
        phase: random(0, animationDuration[1]),
        color: colors[Math.floor(Math.random() * colors.length)]!,
        depth: 0.2 + Math.abs(Math.cos(pitch)) * 0.8,
        brightness: random(0.44, 0.98),
        thickness: random(0.72, 1.22),
      };
    });
    let width = 1;
    let height = 1;
    let pixelRatio = 1;
    let frame = 0;
    let lastDraw = 0;

    colors.forEach((color) => {
      const sprite = document.createElement('canvas');
      sprite.width = 320;
      sprite.height = 3;
      const spriteContext = sprite.getContext('2d')!;
      const gradient = spriteContext.createLinearGradient(0, 0, sprite.width, 0);
      gradient.addColorStop(0, 'rgba(0,0,0,0)');
      gradient.addColorStop(0.46, 'rgba(0,0,0,0)');
      gradient.addColorStop(1, color);
      spriteContext.fillStyle = gradient;
      spriteContext.fillRect(0, 1, sprite.width, 1);
      sprites.set(color, sprite);
    });

    const resize = () => {
      width = Math.max(1, window.innerWidth);
      height = Math.max(1, window.innerHeight);
      pixelRatio = Math.min(window.devicePixelRatio || 1, 1, 1920 / width, 1080 / height);
      canvas.width = Math.round(width * pixelRatio);
      canvas.height = Math.round(height * pixelRatio);
    };

    const draw = (now: number) => {
      frame = requestAnimationFrame(draw);
      if (canvas.closest('.lobby')?.classList.contains('is-paused')) return;
      if (now - lastDraw < 1000 / 30) return;
      lastDraw = now;
      context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
      context.globalAlpha = 1;
      context.fillStyle = '#000';
      context.fillRect(0, 0, width, height);
      const centerX = width * 0.5;
      const centerY = height * 0.5;
      const baseLength = Math.min(width, height) * 0.4;
      const seconds = now / 1000;
      particles.forEach((particle) => {
        const progress = ((seconds + particle.phase) % particle.duration) / particle.duration;
        const length = baseLength * 2 * (1 - progress) * (0.72 + particle.depth * 0.4);
        let alpha = progress < 0.2 ? progress / 0.2 : 1;
        alpha *= particle.brightness;
        if (length < 0.6 || alpha < 0.01) return;
        context.save();
        context.translate(centerX, centerY);
        context.rotate(particle.angle);
        context.globalAlpha = alpha;
        context.drawImage(sprites.get(particle.color)!, 0, -particle.thickness,
          length, Math.max(1, particle.thickness * 2));
        context.restore();
      });
    };

    resize();
    frame = requestAnimationFrame(draw);
    window.addEventListener('resize', resize);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener('resize', resize);
    };
  }, [particleCount, colors, animationDuration]);

  return (
    <div className="particle-perspective" aria-hidden="true">
      <canvas ref={canvasRef} className="time-canvas" />
    </div>
  );
});

type GameCard = {
  id: string;
  title: string;
  subtitle: string;
  index: string;
  accent: string;
  accent2: string;
};

const GAME_CARDS: GameCard[] = [
  { id: 'catch', title: '接水果', subtitle: 'Catch the rhythm of falling color', index: '01', accent: '#06b6d4', accent2: '#0f766e' },
  { id: 'rps', title: '猜拳对决', subtitle: 'Read the moment. Make your move.', index: '02', accent: '#f43f5e', accent2: '#7c3aed' },
  { id: 'bubble', title: '戳泡泡', subtitle: 'Aim softly. Chain every touch.', index: '03', accent: '#84cc16', accent2: '#0891b2' },
  { id: 'tarot', title: '塔罗牌', subtitle: 'Three cards reveal one direction', index: '04', accent: '#d8b4fe', accent2: '#4338ca' },
  { id: 'ninja', title: '水果忍者', subtitle: 'Draw the blade through color', index: '05', accent: '#f97316', accent2: '#e11d48' },
  { id: 'rhythm', title: '节奏大师', subtitle: 'Move precisely inside the beat', index: '06', accent: '#a78bfa', accent2: '#0ea5e9' },
  { id: 'shooter', title: '星际突击', subtitle: 'A new path generated every run', index: '07', accent: '#38bdf8', accent2: '#1d4ed8' },
];

function wrapIndex(index: number, length: number) {
  return ((index % length) + length) % length;
}

function signedOffset(index: number, active: number, length: number) {
  const raw = index - active;
  const alternate = raw > 0 ? raw - length : raw + length;
  return Math.abs(alternate) < Math.abs(raw) ? alternate : raw;
}

function useViewport() {
  const [size, setSize] = React.useState(() => ({
    width: window.innerWidth,
    height: window.innerHeight,
  }));
  React.useEffect(() => {
    let frame = 0;
    const resize = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => setSize({ width: window.innerWidth, height: window.innerHeight }));
    };
    window.addEventListener('resize', resize);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener('resize', resize);
    };
  }, []);
  return size;
}

type CardStackProps = {
  active: number;
  onSelect: (index: number) => void;
  onNavigate: (direction: number) => void;
};

function CardStack({ active, onSelect, onNavigate }: CardStackProps) {
  const reduceMotion = useReducedMotion();
  const viewport = useViewport();
  const cardWidth = Math.round(Math.max(420, Math.min(620, viewport.width * 0.42)));
  const cardHeight = Math.round(Math.max(252, Math.min(372, cardWidth * 0.6)));
  const spacing = Math.round(cardWidth * 0.47);
  const visibleOffset = viewport.width < 1050 ? 1 : 2;

  return (
    <section
      className="card-stage"
      aria-label="游戏选择"
      tabIndex={0}
      onKeyDown={(event) => {
        if (event.key === 'ArrowLeft') onNavigate(-1);
        if (event.key === 'ArrowRight') onNavigate(1);
        if (event.key === 'Enter') window.javaLobby?.launchSelected?.();
      }}
    >
      <div className="stage-halo" />
      <div className="card-perspective">
        <AnimatePresence initial={false}>
          {GAME_CARDS.map((item, index) => {
            const offset = signedOffset(index, active, GAME_CARDS.length);
            const distance = Math.abs(offset);
            if (distance > visibleOffset) return null;
            const isActive = offset === 0;
            const rotate = offset * 23;
            const x = offset * spacing;
            const y = distance * 18 + (isActive ? -22 : 0);
            const z = -distance * 150;

            return (
              <motion.article
                className={`fan-card${isActive ? ' is-active' : ''}`}
                key={item.id}
                style={{
                  width: cardWidth,
                  height: cardHeight,
                  marginLeft: -cardWidth / 2,
                  marginTop: -cardHeight / 2,
                  zIndex: 50 - distance,
                  transformStyle: 'preserve-3d',
                  '--accent': item.accent,
                  '--accent-2': item.accent2,
                } as React.CSSProperties}
                initial={reduceMotion ? false : { opacity: 0, x, y: y + 44, z, rotateZ: rotate, rotateX: 12, scale: 0.92 }}
                animate={{
                  opacity: isActive ? 1 : distance === 1 ? 0.9 : 0.66,
                  x,
                  y,
                  z,
                  rotateZ: rotate,
                  rotateX: isActive ? 0 : 12,
                  scale: isActive ? 1.035 : 0.94,
                }}
                exit={{ opacity: 0, scale: 0.86, y: y + 28 }}
                transition={{ type: 'spring', stiffness: 285, damping: 29, mass: 0.78 }}
                drag={isActive ? 'x' : false}
                dragConstraints={{ left: 0, right: 0 }}
                dragElastic={0.16}
                onDragEnd={(_, info) => {
                  const threshold = Math.min(145, cardWidth * 0.2);
                  if (info.offset.x > threshold || info.velocity.x > 620) onNavigate(-1);
                  if (info.offset.x < -threshold || info.velocity.x < -620) onNavigate(1);
                }}
                onClick={() => onSelect(index)}
              >
                <div className="card-art" />
                <div className="card-grain" />
                <div className="card-sheen" />
                <div className="card-copy">
                  <span className="card-index">{item.index}</span>
                  <h2>{item.title}</h2>
                  <p>{item.subtitle}</p>
                </div>
              </motion.article>
            );
          })}
        </AnimatePresence>
      </div>
      <div className="dots" aria-label="游戏页码">
        {GAME_CARDS.map((item, index) => (
          <button
            type="button"
            key={item.id}
            className={index === active ? 'active' : ''}
            aria-label={`选择 ${item.title}`}
            onClick={() => onSelect(index)}
          />
        ))}
      </div>
    </section>
  );
}

export default function App() {
  const [active, setActive] = React.useState(0);
  const cursorRef = React.useRef<HTMLDivElement>(null);
  const cameraRef = React.useRef<HTMLImageElement>(null);
  const rootRef = React.useRef<HTMLDivElement>(null);

  const select = React.useCallback((index: number) => {
    const next = wrapIndex(index, GAME_CARDS.length);
    setActive(next);
    window.javaLobby?.selectGame?.(next);
  }, []);

  const navigate = React.useCallback((direction: number) => {
    if (window.javaLobby?.navigate) {
      window.javaLobby.navigate(direction);
    } else {
      setActive((value) => wrapIndex(value + direction, GAME_CARDS.length));
    }
  }, []);

  React.useEffect(() => {
    window.gestureLobby = {
      setActive: (index) => setActive(wrapIndex(Math.round(index), GAME_CARDS.length)),
      setConfirmProgress: (progress) => {
        cursorRef.current?.style.setProperty('--confirm-progress', `${Math.max(0, Math.min(1, progress)) * 360}deg`);
        cursorRef.current?.classList.toggle('is-confirming', progress > 0.001);
      },
      setHand: (visible, x, y) => {
        const cursor = cursorRef.current;
        if (!cursor) return;
        cursor.classList.toggle('is-visible', visible);
        cursor.style.transform = `translate3d(${x * window.innerWidth}px, ${y * window.innerHeight}px, 0)`;
      },
      setGesture: (visible, x, y, progress) => {
        const cursor = cursorRef.current;
        if (!cursor) return;
        cursor.classList.toggle('is-visible', visible);
        cursor.style.transform = `translate3d(${x * window.innerWidth}px, ${y * window.innerHeight}px, 0)`;
        cursor.style.setProperty('--confirm-progress', `${Math.max(0, Math.min(1, progress)) * 360}deg`);
        cursor.classList.toggle('is-confirming', progress > 0.001);
      },
      setCameraFrame: (frame) => {
        const camera = cameraRef.current;
        if (!camera || !frame) return;
        camera.src = frame.startsWith('data:') ? frame : `data:image/jpeg;base64,${frame}`;
        camera.closest('.camera-preview')?.classList.add('is-live');
      },
      activate: () => rootRef.current?.classList.remove('is-paused'),
      pause: () => rootRef.current?.classList.add('is-paused'),
    };
    window.javaLobby?.onWebReady?.();
    return () => {
      delete window.gestureLobby;
    };
  }, []);

  return (
    <main className="lobby" ref={rootRef}>
      <ParticleAnimation />
      <div className="background-vignette" />
      <button className="api-button" type="button" onClick={() => window.javaLobby?.openApiSettings?.()}>
        <Settings2 size={15} strokeWidth={1.6} />
        <span>API</span>
      </button>
      <div className="camera-preview" aria-label="手势摄像头预览">
        <img ref={cameraRef} alt="" />
        <span>LIVE</span>
      </div>
      <CardStack active={active} onSelect={select} onNavigate={navigate} />
      <div className="hand-cursor" ref={cursorRef} aria-hidden="true">
        <i />
      </div>
    </main>
  );
}

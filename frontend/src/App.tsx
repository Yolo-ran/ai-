import { useRef, useState } from 'react';
import { motion, useScroll, useTransform } from 'framer-motion';
import { ArrowRight, Check } from 'lucide-react';
import AnimatedLetter from './components/AnimatedLetter';
import WordsPullUp from './components/WordsPullUp';
import WordsPullUpMultiStyle from './components/WordsPullUpMultiStyle';

const navigationItems = [
  { label: 'Our story', href: '#about' },
  { label: 'Collective', href: '#about' },
  { label: 'Workshops', href: '#features' },
  { label: 'Programs', href: '#features' },
  { label: 'Inquiries', href: '#features' },
];

const featureCards = [
  {
    number: '01',
    title: 'Project Storyboard.',
    image:
      'https://images.higgs.ai/?default=1&output=webp&url=https%3A%2F%2Fd8j0ntlcm91z4.cloudfront.net%2Fuser_38xzZboKViGWJOttwIXH07lWA1P%2Fhf_20260405_171918_4a5edc79-d78f-4637-ac8b-53c43c220606.png&w=1280&q=85',
    bullets: [
      'Scene planning with mood-first sequencing.',
      'Shot rhythm, framing, and visual continuity.',
      'Creative alignment before production begins.',
      'Fast iteration for cinematic story flow.',
    ],
  },
  {
    number: '02',
    title: 'Smart Critiques.',
    image:
      'https://images.higgs.ai/?default=1&output=webp&url=https%3A%2F%2Fd8j0ntlcm91z4.cloudfront.net%2Fuser_38xzZboKViGWJOttwIXH07lWA1P%2Fhf_20260405_171741_ed9845ab-f5b2-4018-8ce7-07cc01823522.png&w=1280&q=85',
    bullets: [
      'AI analysis for pacing and composition.',
      'Creative notes tailored to the project tone.',
      'Tool integrations that keep feedback actionable.',
    ],
  },
  {
    number: '03',
    title: 'Immersion Capsule.',
    image:
      'https://images.higgs.ai/?default=1&output=webp&url=https%3A%2F%2Fd8j0ntlcm91z4.cloudfront.net%2Fuser_38xzZboKViGWJOttwIXH07lWA1P%2Fhf_20260405_171809_f56666dc-c099-4778-ad82-9ad4f209567b.png&w=1280&q=85',
    bullets: [
      'Notification silencing for uninterrupted focus.',
      'Ambient soundscapes for deeper immersion.',
      'Schedule syncing to protect creative flow.',
    ],
  },
];

export default function App() {
  const heroRef = useRef<HTMLElement>(null);
  const aboutRef = useRef<HTMLElement>(null);
  const [hoveredNav, setHoveredNav] = useState<string | null>(null);
  const { scrollYProgress } = useScroll({
    target: heroRef,
    offset: ['start start', 'end start'],
  });

  const heroOpacity = useTransform(scrollYProgress, [0, 0.75], [1, 0.38]);
  const heroY = useTransform(scrollYProgress, [0, 1], [0, 80]);

  return (
    <main className="bg-black text-primary">
      <section
        id="home"
        ref={heroRef}
        className="relative flex h-screen items-stretch bg-black p-4 md:p-6"
      >
        <div className="relative h-full w-full overflow-hidden rounded-[2rem] border border-white/10 bg-black shadow-glow">
          <video
            className="absolute inset-0 h-full w-full object-cover"
            src="https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260405_170732_8a9ccda6-5cff-4628-b164-059c500a2b41.mp4"
            autoPlay
            loop
            muted
            playsInline
          />
          <div className="noise-overlay pointer-events-none absolute inset-0 opacity-[0.7] mix-blend-overlay" />
          <div className="absolute inset-0 bg-gradient-to-b from-black/30 via-transparent to-black/60" />

          <motion.div
            style={{ opacity: heroOpacity, y: heroY }}
            className="relative z-10 flex h-full flex-col justify-between px-5 pb-8 pt-6 sm:px-8 sm:pb-10 sm:pt-8 md:px-10 lg:px-14 lg:pb-14"
          >
            <div className="absolute left-1/2 top-0 z-20 -translate-x-1/2">
              <nav className="inline-flex flex-wrap items-center justify-center gap-3 rounded-b-2xl bg-black px-4 py-2 sm:gap-6 md:rounded-b-3xl md:px-8 lg:gap-14 md:gap-12">
                {navigationItems.map((item) => (
                  <a
                    key={item.label}
                    href={item.href}
                    className="rounded-full px-1 py-1 text-[10px] transition-colors duration-300 sm:text-xs md:text-sm"
                    style={{
                      color:
                        hoveredNav === item.label ? '#E1E0CC' : 'rgba(225, 224, 204, 0.8)',
                    }}
                    onMouseEnter={() => setHoveredNav(item.label)}
                    onMouseLeave={() => setHoveredNav(null)}
                  >
                    {item.label}
                  </a>
                ))}
              </nav>
            </div>

            <div className="absolute bottom-0 left-0 right-0 grid grid-cols-1 items-end gap-8 pb-2 lg:grid-cols-12 lg:gap-10">
              <div className="lg:col-span-8">
                <WordsPullUp
                  text="Prisma"
                  trailingAsterisk
                  className="text-[26vw] font-medium leading-[0.85] tracking-[-0.07em] sm:text-[24vw] md:text-[22vw] lg:text-[20vw] xl:text-[19vw] 2xl:text-[20vw]"
                />
              </div>

              <motion.div
                initial={{ opacity: 0, y: 34 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, amount: 0.55 }}
                transition={{ duration: 0.85, ease: [0.16, 1, 0.3, 1], delay: 0.5 }}
                className="max-w-sm lg:col-span-4 lg:justify-self-end"
              >
                <p
                  className="text-xs leading-[1.2] text-primary/70 sm:text-sm md:text-base"
                >
                  Prisma is a worldwide network of visual artists, filmmakers and
                  storytellers bound not by place, status or labels but by passion and
                  hunger to unlock potential through our unique perspectives.
                </p>
                <motion.a
                  href="#features"
                  whileHover={{ y: -2 }}
                  whileTap={{ scale: 0.98 }}
                  transition={{ duration: 0.85, ease: [0.16, 1, 0.3, 1], delay: 0.7 }}
                  className="group mt-6 inline-flex items-center gap-2 rounded-full bg-primary px-5 py-1.5 text-sm font-medium text-black transition-all duration-300 hover:gap-3 sm:mt-8 sm:px-6 sm:py-2 sm:text-base"
                >
                  <span>Join the lab</span>
                  <span className="inline-flex h-9 w-9 items-center justify-center rounded-full bg-black text-primary transition-transform duration-300 group-hover:scale-110 sm:h-10 sm:w-10">
                    <ArrowRight size={18} />
                  </span>
                </motion.a>
              </motion.div>
            </div>
          </motion.div>
        </div>
      </section>

      <section id="about" ref={aboutRef} className="bg-black px-4 py-24 md:px-6 md:py-32">
        <div className="mx-auto max-w-6xl">
          <div
            className="rounded-[2rem] border border-white/10 px-6 py-10 shadow-glow sm:px-8 md:px-12 md:py-14"
            style={{ backgroundColor: '#101010' }}
          >
            <motion.p
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, amount: 0.7 }}
              transition={{ duration: 0.7 }}
              className="mb-6 text-xs uppercase tracking-[0.28em] text-gray-500"
            >
              Visual arts
            </motion.p>

            <WordsPullUpMultiStyle
              className="mx-auto max-w-3xl text-center text-3xl font-normal leading-[0.95] sm:text-4xl sm:leading-[0.9] md:text-5xl lg:text-6xl xl:text-7xl"
              lineClassName="inline-flex flex-wrap justify-center gap-x-3 gap-y-2"
              lines={[
                [{ text: 'I am Marcus Chen,', className: 'text-primary' }],
                [{ text: 'a self-taught director.', className: 'font-serif italic text-primary' }],
                [
                  {
                    text: 'I have skills in color grading, visual effects, and narrative design.',
                    className: 'text-primary',
                  },
                ],
              ]}
            />

            <motion.div
              initial={{ opacity: 0, y: 24 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, amount: 0.4 }}
              transition={{ duration: 0.8, delay: 0.15, ease: [0.16, 1, 0.3, 1] }}
              className="mx-auto mt-10 max-w-4xl text-center"
            >
              <AnimatedLetter
                targetRef={aboutRef}
                className="text-xs text-[#DEDBC8] sm:text-sm md:text-base"
                text="Over the last seven years, I have worked with Parallax, a Berlin-based production house that crafts cinema, series, and Noir Studio in Paris. Together, we have created work that has earned international acclaim at several major festivals."
              />
            </motion.div>
          </div>
        </div>
      </section>

      <section id="features" className="relative min-h-screen overflow-hidden bg-black px-4 py-24 md:px-6 md:py-32">
        <div className="bg-noise pointer-events-none absolute inset-0 opacity-15" />

        <div className="relative z-10 mx-auto max-w-7xl">
          <div className="max-w-4xl">
            <WordsPullUpMultiStyle
              className="space-y-2 text-xl font-normal sm:text-2xl md:text-3xl lg:text-4xl"
              lineClassName="inline-flex flex-wrap gap-x-3 gap-y-2"
              lines={[
                [
                  {
                    text: 'Studio-grade workflows for visionary creators.',
                    className: 'text-primary',
                  },
                ],
                [
                  {
                    text: 'Built for pure vision. Powered by art.',
                    className: 'text-gray-500',
                  },
                ],
              ]}
            />
          </div>

          <div className="mt-14 grid grid-cols-1 gap-3 md:grid-cols-2 md:gap-2 lg:h-[480px] lg:grid-cols-4 lg:gap-1">
            <motion.article
              initial={{ opacity: 0, scale: 0.95 }}
              whileInView={{ opacity: 1, scale: 1 }}
              viewport={{ once: true, margin: '-100px' }}
              transition={{ duration: 0.85, ease: [0.22, 1, 0.36, 1] }}
              className="group relative min-h-[460px] overflow-hidden rounded-[1.75rem] border border-white/10"
              style={{ backgroundColor: '#212121' }}
            >
              <video
                className="absolute inset-0 h-full w-full object-cover transition-transform duration-700 group-hover:scale-105"
                src="https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260406_133058_0504132a-0cf3-4450-a370-8ea3b05c95d4.mp4"
                autoPlay
                loop
                muted
                playsInline
              />
              <div className="absolute inset-0 bg-[linear-gradient(180deg,rgba(0,0,0,0.18)_0%,rgba(0,0,0,0.82)_100%)]" />
              <div className="absolute inset-x-0 top-0 flex items-center justify-between px-6 py-6">
                <span className="rounded-full border border-white/10 bg-black/40 px-3 py-1 text-xs tracking-[0.26em] text-gray-300">
                  01
                </span>
                <span className="text-xs uppercase tracking-[0.26em] text-gray-400">Direction</span>
              </div>
              <div className="absolute inset-x-0 bottom-0 p-6">
                <p className="text-2xl font-bold leading-tight" style={{ color: '#E1E0CC' }}>
                  Your creative canvas.
                </p>
              </div>
            </motion.article>

            {featureCards.map((card, index) => (
              <motion.article
                key={card.title}
                initial={{ opacity: 0, scale: 0.95 }}
                whileInView={{ opacity: 1, scale: 1 }}
                viewport={{ once: true, margin: '-100px' }}
                transition={{
                  duration: 0.85,
                  ease: [0.22, 1, 0.36, 1],
                  delay: 0.15 + index * 0.15,
                }}
                className="flex min-h-[460px] flex-col rounded-[1.75rem] border border-white/10 p-5 transition-transform duration-300 hover:-translate-y-1"
                style={{ backgroundColor: '#212121' }}
              >
                <div className="overflow-hidden rounded-[1.25rem] border border-white/10">
                  <img
                    src={card.image}
                    alt={card.title}
                    className="h-10 w-10 rounded object-cover sm:h-12 sm:w-12"
                  />
                </div>

                <div className="mt-5 flex items-start justify-between gap-4">
                  <div>
                    <h3 className="text-2xl font-normal" style={{ color: '#E1E0CC' }}>
                      {card.title}
                    </h3>
                    <p className="mt-2 text-xs uppercase tracking-[0.28em] text-gray-500">{card.number}</p>
                  </div>
                </div>

                <ul className="mt-6 space-y-4">
                  {card.bullets.map((bullet) => (
                    <li key={bullet} className="flex items-start gap-3 text-sm leading-6 text-gray-400">
                      <Check className="mt-1 h-4 w-4 shrink-0 text-primary" />
                      <span>{bullet}</span>
                    </li>
                  ))}
                </ul>

                <a
                  href="#home"
                  className="mt-auto inline-flex items-center gap-3 pt-8 text-sm font-bold uppercase tracking-[0.22em] text-primary transition-opacity duration-300 hover:opacity-80"
                >
                  <span>Learn more</span>
                  <ArrowRight size={16} style={{ transform: 'rotate(-45deg)' }} />
                </a>
              </motion.article>
            ))}
          </div>
        </div>
      </section>
    </main>
  );
}

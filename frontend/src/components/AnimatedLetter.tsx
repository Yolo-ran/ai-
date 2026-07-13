import { RefObject, useRef } from 'react';
import { motion, MotionValue, useScroll, useTransform } from 'framer-motion';

type AnimatedLetterProps = {
  text: string;
  className?: string;
  targetRef?: RefObject<HTMLElement>;
};

type LetterProps = {
  char: string;
  index: number;
  total: number;
  progress: MotionValue<number>;
};

function Letter({ char, index, total, progress }: LetterProps) {
  const charProgress = index / total;
  const opacity = useTransform(
    progress,
    [Math.max(charProgress - 0.1, 0), Math.min(charProgress + 0.05, 1)],
    [0.2, 1],
  );
  const y = useTransform(
    progress,
    [Math.max(charProgress - 0.1, 0), Math.min(charProgress + 0.05, 1)],
    [8, 0],
  );

  return (
    <motion.span style={{ opacity, y }} className="inline-block">
      {char === ' ' ? '\u00A0' : char}
    </motion.span>
  );
}

export default function AnimatedLetter({ text, className, targetRef }: AnimatedLetterProps) {
  const internalRef = useRef<HTMLParagraphElement>(null);
  const scrollTarget = targetRef ?? internalRef;
  const { scrollYProgress } = useScroll({
    target: scrollTarget,
    offset: ['start 0.8', 'end 0.2'],
  });
  const characters = Array.from(text);

  return (
    <p ref={internalRef} className={className}>
      {characters.map((char, index) => (
        <Letter
          key={`${char}-${index}`}
          char={char}
          index={index}
          total={characters.length}
          progress={scrollYProgress}
        />
      ))}
    </p>
  );
}

import { motion } from 'framer-motion';

export type PullUpSegment = {
  text: string;
  className?: string;
};

type WordsPullUpMultiStyleProps = {
  lines: PullUpSegment[][];
  className?: string;
  lineClassName?: string;
};

const wordTransition = {
  duration: 0.72,
  ease: [0.16, 1, 0.3, 1] as const,
};

export default function WordsPullUpMultiStyle({
  lines,
  className,
  lineClassName,
}: WordsPullUpMultiStyleProps) {
  let wordIndex = 0;

  return (
    <motion.div
      className={className}
      initial="hidden"
      whileInView="visible"
      viewport={{ once: true, amount: 0.4 }}
      variants={{
        hidden: {},
        visible: {
          transition: {
            delayChildren: 0.05,
            staggerChildren: 0.05,
          },
        },
      }}
    >
      {lines.map((line, lineIndex) => (
        <div
          key={`line-${lineIndex}`}
          className={lineClassName ?? 'flex flex-wrap items-end justify-center gap-x-3 gap-y-2'}
        >
          {line.map((segment, segmentIndex) => {
            const words = segment.text.split(' ');

            return (
              <span key={`segment-${lineIndex}-${segmentIndex}`} className={segment.className}>
                {words.map((word, index) => {
                  const currentWord = wordIndex++;

                  return (
                    <span
                      key={`${word}-${lineIndex}-${segmentIndex}-${index}`}
                      className="inline-block overflow-hidden"
                    >
                      <motion.span
                        className="mr-[0.22em] inline-block"
                        variants={{
                          hidden: { y: '108%', opacity: 0 },
                          visible: {
                            y: '0%',
                            opacity: 1,
                            transition: {
                              ...wordTransition,
                              delay: currentWord * 0.02,
                            },
                          },
                        }}
                      >
                        {word}
                      </motion.span>
                    </span>
                  );
                })}
              </span>
            );
          })}
        </div>
      ))}
    </motion.div>
  );
}

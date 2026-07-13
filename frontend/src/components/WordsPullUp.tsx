import { motion } from 'framer-motion';

type WordsPullUpProps = {
  text: string;
  className?: string;
  trailingAsterisk?: boolean;
};

const charTransition = {
  duration: 0.7,
  ease: [0.16, 1, 0.3, 1] as const,
};

export default function WordsPullUp({
  text,
  className,
  trailingAsterisk = false,
}: WordsPullUpProps) {
  const characters = Array.from(text);

  return (
    <motion.div
      className={className}
      initial="hidden"
      whileInView="visible"
      viewport={{ once: true, amount: 0.75 }}
      variants={{
        hidden: {},
        visible: {
          transition: {
            staggerChildren: 0.045,
          },
        },
      }}
    >
      {characters.map((character, index) => {
        const isLast = index === characters.length - 1;

        return (
          <span key={`${character}-${index}`} className="inline-block overflow-hidden align-top">
            <motion.span
              className="relative inline-block"
              variants={{
                hidden: { y: '110%', opacity: 0 },
                visible: { y: '0%', opacity: 1, transition: charTransition },
              }}
            >
              {character === ' ' ? '\u00A0' : character}
              {trailingAsterisk && isLast ? (
                <motion.sup
                  className="absolute -right-[0.3em] top-[0.65em] text-[0.31em] font-light leading-none"
                  variants={{
                    hidden: { opacity: 0, y: 12 },
                    visible: { opacity: 1, y: 0, transition: { ...charTransition, delay: 0.12 } },
                  }}
                >
                  *
                </motion.sup>
              ) : null}
            </motion.span>
          </span>
        );
      })}
    </motion.div>
  );
}

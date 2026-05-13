/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx,ts,tsx}"],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        // ----- Dark "ink" surface tones (the backdrop) -----
        ink: {
          950: "#06070a",
          900: "#0a0c0f",     // body background
          800: "#0f1318",     // panel base
          700: "#161b22",
          600: "#1f242c",
          500: "#3a3f47",
          400: "#5d6168",
          300: "#8b8f96",
        },
        // ----- Paper / cream tones (text on dark) -----
        paper: {
          50:  "#faf6ec",     // primary text
          100: "#f3ecda",
          200: "#e2d9bf",     // muted high-emphasis
          300: "#c4b894",
          400: "#988e72",
          500: "#6e6753",
        },
        // ----- Brass / aged-gold (primary accent: routes, action, "car") -----
        brass: {
          200: "#f0dcaa",
          300: "#e2c585",
          400: "#d4b06b",
          500: "#c9a35a",
          600: "#a4823f",
          700: "#7c632f",
          800: "#5b4923",
        },
        // ----- Oxblood / aged map-ink red (alerts, "foot" profile) -----
        oxblood: {
          300: "#e5917b",
          400: "#d76b56",
          500: "#c8553d",
          600: "#a64030",
          700: "#7d3024",
        },
        // ----- Lake teal (cool secondary, "bike" profile, isochrones) -----
        lake: {
          300: "#8ec8c4",
          400: "#6eb5b0",
          500: "#5fa8a4",
          600: "#458a87",
          700: "#326967",
        },
      },
      fontFamily: {
        sans:    ['"Manrope"',          "ui-sans-serif", "system-ui", "sans-serif"],
        display: ['"Fraunces"',         "ui-serif",      "Georgia",   "serif"],
        mono:    ['"JetBrains Mono"',   "ui-monospace",  "Menlo",     "monospace"],
      },
      letterSpacing: {
        "atlas": "0.22em",
      },
      animation: {
        "fade-in":   "fadeIn 240ms ease-out both",
        "fade-up":   "fadeUp 320ms cubic-bezier(0.16, 1, 0.3, 1) both",
        "pulse-pin": "pulsePin 1.6s ease-out infinite",
        "shimmer":   "shimmer 1.8s ease-in-out infinite",
        "drop":      "drop 360ms cubic-bezier(0.16, 1, 0.3, 1) both",
        "ink-bleed": "inkBleed 700ms ease-out forwards",
      },
      keyframes: {
        fadeIn:   { "0%": { opacity: "0" }, "100%": { opacity: "1" } },
        fadeUp:   { "0%": { opacity: "0", transform: "translateY(8px)" },
                    "100%": { opacity: "1", transform: "translateY(0)" } },
        drop:     { "0%": { opacity: "0", transform: "translateY(-12px) scale(0.96)" },
                    "100%": { opacity: "1", transform: "translateY(0) scale(1)" } },
        pulsePin: { "0%":   { transform: "translate(-50%,-50%) scale(1)",   opacity: "0.7" },
                    "100%": { transform: "translate(-50%,-50%) scale(2.6)", opacity: "0" } },
        shimmer:  { "0%":   { backgroundPosition: "-200% 0" },
                    "100%": { backgroundPosition: "200% 0" } },
        inkBleed: { "0%":   { opacity: "0", filter: "blur(8px)" },
                    "100%": { opacity: "1", filter: "blur(0)" } },
      },
      boxShadow: {
        // Inner brass hairline (panel highlight).
        "edge": "inset 0 1px 0 0 rgba(212, 176, 107, 0.08), inset 0 0 0 1px rgba(212, 176, 107, 0.06)",
        "edge-strong": "inset 0 1px 0 0 rgba(212, 176, 107, 0.18), inset 0 0 0 1px rgba(212, 176, 107, 0.12)",
        // Cast shadow for cards.
        "atlas": "0 30px 60px -20px rgba(0, 0, 0, 0.7), 0 8px 24px -10px rgba(0, 0, 0, 0.5)",
        // Brass focus glow.
        "brass": "0 0 0 1px rgba(201, 163, 90, 0.45), 0 10px 30px -8px rgba(201, 163, 90, 0.25)",
      },
    },
  },
  plugins: [],
};

import { cycleTheme, type Theme, useTheme } from "../lib/theme";

const LABEL: Record<Theme, string> = { system: "Sistema", light: "Claro", dark: "Oscuro" };
const ICON: Record<Theme, string> = {
  system: "M4 5h16v11H4zM9 20h6M12 16v4",
  light: "M12 4v2m0 12v2M4 12h2m12 0h2M6.3 6.3l1.4 1.4m8.6 8.6l1.4 1.4M6.3 17.7l1.4-1.4m8.6-8.6l1.4-1.4M12 8a4 4 0 100 8 4 4 0 000-8z",
  dark: "M20 14.5A8 8 0 019.5 4 8 8 0 1020 14.5z",
};

export function ThemeToggle() {
  const theme = useTheme();
  return (
    <button
      type="button"
      className="btn ghost icon-only theme-toggle"
      onClick={cycleTheme}
      aria-label={`Tema: ${LABEL[theme]}. Cambiar`}
      title={`Tema: ${LABEL[theme]}`}
    >
      <svg viewBox="0 0 24 24" aria-hidden="true" className="icon">
        <path d={ICON[theme]} />
      </svg>
    </button>
  );
}

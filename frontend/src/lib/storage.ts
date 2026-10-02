/** localStorage puede lanzar (modo privado, cuota, bloqueado): la app debe funcionar sin él. */
export const storage = {
  get(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  },
  set(key: string, value: string): void {
    try {
      localStorage.setItem(key, value);
    } catch {
      /* sin caché: no es grave */
    }
  },
  remove(key: string): void {
    try {
      localStorage.removeItem(key);
    } catch {
      /* nada que hacer */
    }
  },
};

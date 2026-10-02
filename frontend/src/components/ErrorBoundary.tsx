import { Component, type ErrorInfo, type ReactNode } from "react";

interface State {
  failed: boolean;
}

/** Evita la pantalla en blanco: un error de render en un hijo muestra un mensaje recuperable. */
export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { failed: false };

  static getDerivedStateFromError(): State {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error(error, info.componentStack); // aquí iría el reporte a Sentry/etc.
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <div role="alert">
        <p>Algo salió mal.</p>
        <button type="button" onClick={() => this.setState({ failed: false })}>
          Reintentar
        </button>
      </div>
    );
  }
}

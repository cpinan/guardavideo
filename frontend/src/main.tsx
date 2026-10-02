import { QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import { persistJobs } from "./lib/persist";
import { createQueryClient } from "./lib/retry";
import "./styles.css";

const queryClient = createQueryClient();
persistJobs(queryClient); // guarda la lista en localStorage para pintar al instante tras recargar

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </React.StrictMode>,
);

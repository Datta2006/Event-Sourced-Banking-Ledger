/** Single source for the optional live API base. Unset = mock transport. */
export const API_BASE = import.meta.env.VITE_API_BASE ?? '';

/** True when the UI should talk to the gateway instead of the mock server. */
export const isLive = Boolean(import.meta.env.VITE_API_BASE);

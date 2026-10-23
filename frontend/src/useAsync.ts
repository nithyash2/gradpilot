import { useCallback, useEffect, useState } from 'react';
import { ApiError } from './api';

/** Loads data once, with a reload function for after changes. */
export function useAsync<T>(load: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(load, deps);

  const reload = useCallback(async () => {
    try {
      const result = await run();
      setData(result);
      setError(null);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, [run]);

  useEffect(() => {
    setLoading(true);
    void reload();
  }, [reload]);

  return { data, setData, error, loading, reload };
}

export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    const fieldErrors = Object.entries(e.fields);
    if (fieldErrors.length > 0) {
      return fieldErrors.map(([field, message]) => `${field}: ${message}`).join('. ');
    }
    return e.message;
  }
  if (e instanceof Error) return e.message;
  return 'Something went wrong. Try again.';
}

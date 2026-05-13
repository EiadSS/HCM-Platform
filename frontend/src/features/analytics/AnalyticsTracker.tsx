import { useEffect } from "react";
import { useLocation } from "react-router-dom";
import { api } from "../../api/client";

export function AnalyticsTracker() {
  const location = useLocation();

  useEffect(() => {
    if (location.pathname === "/analytics") {
      return;
    }
    const path = `${location.pathname}${location.search}`;
    void api
      .recordAnalyticsEvent({
        eventType: "PAGE_VIEW",
        path,
        referrer: document.referrer || undefined
      })
      .catch(() => undefined);
  }, [location.pathname, location.search]);

  return null;
}

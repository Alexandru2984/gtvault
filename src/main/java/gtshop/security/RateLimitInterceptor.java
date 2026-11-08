package gtshop.security;

import gtshop.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private final RateLimiter limiter;
    private final ClientIpResolver ipResolver;
    private final CurrentUser currentUser;

    public RateLimitInterceptor(RateLimiter limiter, ClientIpResolver ipResolver, CurrentUser currentUser) {
        this.limiter = limiter;
        this.ipResolver = ipResolver;
        this.currentUser = currentUser;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RateLimit[] limits = method.getMethod().getAnnotationsByType(RateLimit.class);
        if (limits.length == 0) {
            return true;
        }
        String ip = ipResolver.resolve(request);
        for (RateLimit rl : limits) {
            String subject = rl.scope() == RateLimit.Scope.IP ? "ip:" + ip
                    : currentUser.optionalId().map(id -> "u:" + id).orElse("ip:" + ip);
            String key = rl.name() + "|" + subject;
            if (!limiter.tryAcquire(key, rl.limit(), rl.windowSeconds() * 1000L)) {
                throw ApiException.tooManyRequests("Prea multe cereri. Încearcă din nou puțin mai târziu.");
            }
        }
        return true;
    }
}

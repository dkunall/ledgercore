package com.ledgercore.shared.web;

import com.ledgercore.shared.domain.Actor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets controllers take an {@link Actor} parameter instead of depending on Spring Security types.
 */
public class ActorArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String OPS_AUTHORITY = "ROLE_OPS";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(Actor.class);
    }

    @Override
    public Actor resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest request,
                                 WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("Actor requested on an unauthenticated request");
        }
        boolean ops = auth.getAuthorities().stream().anyMatch(a -> OPS_AUTHORITY.equals(a.getAuthority()));
        return new Actor(auth.getName(), ops);
    }
}

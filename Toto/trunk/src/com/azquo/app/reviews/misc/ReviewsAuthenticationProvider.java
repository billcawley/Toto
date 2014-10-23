package com.azquo.app.reviews.misc;

import com.azquo.app.reviews.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by cawley on 23/10/14.
 */

@Component
public class ReviewsAuthenticationProvider implements AuthenticationProvider {


    @Autowired
    private ReviewService reviewService;

    @Override
    public Authentication authenticate(Authentication authentication)
       {
        String email = authentication.getName(); // they call it name but it will be email
        String password = authentication.getCredentials().toString();
        // use the credentials to try to authenticate against the third party system
        if (reviewService.authenticateUser(email, password)) {
            List<GrantedAuthority> grantedAuths = new ArrayList<GrantedAuthority>();
            grantedAuths.add(new SimpleGrantedAuthority("ROLE_USER"));
            return new UsernamePasswordAuthenticationToken(email, password, grantedAuths);
        } else {
            return null;
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}

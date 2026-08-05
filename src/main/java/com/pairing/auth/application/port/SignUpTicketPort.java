package com.pairing.auth.application.port;

import java.time.Duration;
import java.util.Optional;

public interface SignUpTicketPort {

    void save(String ticket, SignUpTicket data, Duration ttl);

    Optional<SignUpTicket> find(String ticket);

    void delete(String ticket);
}

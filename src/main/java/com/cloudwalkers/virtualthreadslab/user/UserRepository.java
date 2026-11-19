package com.cloudwalkers.virtualthreadslab.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	boolean existsByUsernameIgnoreCase(String username);

	boolean existsByEmailIgnoreCase(String email);

	boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);

	boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

}

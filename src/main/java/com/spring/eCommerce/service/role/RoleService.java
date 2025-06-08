package com.spring.eCommerce.service.role;

import com.spring.eCommerce.entity.Role;
import com.spring.eCommerce.repository.RoleRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepo roleRepo;

    public Role findById(Long id) {
        return roleRepo.findById(id).orElse(null);
    }
    public List<Role> findAll() {
        return roleRepo.findAll();
    }
    public Role findByName(String username) {
        return roleRepo.findByName(username).orElse(null);
    }
    public Role save(Role role) {
        return roleRepo.save(role);
    }


}

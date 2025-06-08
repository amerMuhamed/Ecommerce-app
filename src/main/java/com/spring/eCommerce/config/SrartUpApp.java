package com.spring.eCommerce.config;

import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.entity.Role;
import com.spring.eCommerce.service.role.RoleService;
import com.spring.eCommerce.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SrartUpApp implements CommandLineRunner {
private final UserService userService;
    private final RoleService roleService;

    @Override
    public void run(String... args) throws Exception {
        if(roleService.findAll().isEmpty()){
            System.out.println("Adding Roles");
            roleService.save(new Role(null,"admin"));
            roleService.save(new Role(null,"user"));
            roleService.save(new Role(null,"employee"));
            System.out.println("Roles Added");
        }

        Set<Role> adminRole=new HashSet<>();
        adminRole.add(roleService.findByName("admin"));
        Set<Role> userRole=new HashSet<>();
        userRole.add(roleService.findByName("user"));
        Set<Role> empRole=new HashSet<>();
        empRole.add(roleService.findByName("employee"));

        if(userService.findAll().isEmpty()){
            System.out.println("Adding Users");
            userService.save(new AppUser(null,"amer mohamed","amer","123",adminRole));
            userService.save(new AppUser(null,"ali ali","ali","123",userRole));
            userService.save(new AppUser(null,"omar omar","omar","123",empRole));
            System.out.println("Users Added");
        }

    }
}

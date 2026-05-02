package com.spring.eCommerce.service.comman;

import java.util.List;

public interface CommonService<E> {
    public List<E> getAll();

    public E getById(Long id);

    public E save(E obj);

    public void delete(E obj);

    public void deleteById(Long id);

    public E update(E obj);

}

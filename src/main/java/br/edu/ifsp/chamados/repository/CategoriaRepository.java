package br.edu.ifsp.chamados.repository;

import br.edu.ifsp.chamados.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    @Query(value = "SELECT * FROM categoria ORDER BY nome", nativeQuery = true)
    List<Categoria> findAllByOrderByNome();

}

package br.edu.ifsp.chamados.repository;

import br.edu.ifsp.chamados.model.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @EntityGraph(attributePaths = "departamento")
    Optional<Usuario> findByEmailIgnoreCase(String email);

    @Query(value = "SELECT * FROM usuario ORDER BY nome", nativeQuery = true)
    List<Usuario> findAllByOrderByNome();

    @Query(value = "SELECT * FROM usuario WHERE papel IN (:papeis) ORDER BY nome", nativeQuery = true)
    List<Usuario> findByPapelInOrderByNome(@Param("papeis") Collection<String> papeis);
}
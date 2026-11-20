package br.edu.ifsp.chamados.repository;

import br.edu.ifsp.chamados.model.AnexoChamado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnexoChamadoRepository extends JpaRepository<AnexoChamado, Long> {

    @Query("""
            SELECT a FROM AnexoChamado a
            JOIN FETCH a.enviadoPor
            WHERE a.chamado.id = :chamadoId
            ORDER BY a.dataUpload DESC, a.id DESC
            """)
    List<AnexoChamado> findByChamadoIdWithUsuario(@Param("chamadoId") Long chamadoId);
}
package br.edu.ifsp.chamados.repository;

import br.edu.ifsp.chamados.model.HistoricoChamado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HistoricoChamadoRepository extends JpaRepository<HistoricoChamado, Long> {

    @Query(value = """
                SELECT * FROM historico_chamado 
                WHERE id_chamado = :idChamado
                ORDER BY data_modificacao DESC, id_historico DESC
                """, nativeQuery = true)
    List<HistoricoChamado> findByIdChamadoOrderByDataModificacaoDescIdDesc(@Param("idChamado")Long idChamado);
}

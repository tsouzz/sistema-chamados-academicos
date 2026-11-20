package br.edu.ifsp.chamados.security;

import br.edu.ifsp.chamados.model.Papel;
import br.edu.ifsp.chamados.model.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UsuarioAutenticado implements UserDetails {

    private final Long id;
    private final String nome;
    private final String email;
    private final String senhaHash;
    private final Papel papel;
    private final Long departamentoId;
    private final String departamentoSigla;

    public UsuarioAutenticado(Usuario usuario) {
        this.id = usuario.getId();
        this.nome = usuario.getNome();
        this.email = usuario.getEmail();
        this.senhaHash = usuario.getSenhaHash();
        this.papel = usuario.getPapel();
        this.departamentoId = usuario.getDepartamento() != null ? usuario.getDepartamento().getId() : null;
        this.departamentoSigla = usuario.getDepartamento() != null ? usuario.getDepartamento().getSigla() : null;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public Papel getPapel() {
        return papel;
    }

    public Long getDepartamentoId() {
        return departamentoId;
    }

    public String getDepartamentoSigla() {
        return departamentoSigla;
    }

    public boolean isAtendente() {
        return papel == Papel.TECNICO || papel == Papel.GESTOR;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + papel.name()));
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
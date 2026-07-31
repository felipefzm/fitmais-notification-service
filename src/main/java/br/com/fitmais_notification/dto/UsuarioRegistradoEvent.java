package br.com.fitmais_notification.dto;

import java.time.LocalDateTime;

public record UsuarioRegistradoEvent(Long usuarioId, String username, String email, LocalDateTime registradoEm) {
}


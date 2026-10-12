package pe.edu.vidasalud.citas;

import org.springframework.http.HttpStatus;

public class ReglaException extends RuntimeException {
    private final HttpStatus status;
    private final String codigo;

    public ReglaException(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus status() { return status; }
    public String codigo() { return codigo; }

    public static ReglaException inexistente() {
        return new ReglaException(HttpStatus.NOT_FOUND, "CITA_NO_ENCONTRADA", "La cita no existe.");
    }

    public static ReglaException invalido(String mensaje) {
        return new ReglaException(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", mensaje);
    }

    public static ReglaException transicion() {
        return new ReglaException(HttpStatus.CONFLICT, "TRANSICION_NO_PERMITIDA",
                "El estado actual de la cita no permite esta operacion.");
    }

    public static ReglaException horario() {
        return new ReglaException(HttpStatus.CONFLICT, "HORARIO_OCUPADO",
                "El paciente o profesional ya tiene una reserva en ese horario.");
    }
}


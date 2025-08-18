package com.calcite_new.sql.model.enums;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Column;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Arrays;

@Embeddable
@Data
@NoArgsConstructor
public class ErrorDescription {

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    public ErrorDescription(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public static ErrorDescription of(String errorMessage) {
        return new ErrorDescription(errorMessage);
    }

    public static ErrorDescription of(Exception exception) {
        StringBuilder sb = new StringBuilder();
        sb.append(exception.toString()).append("\n");
        Arrays.stream(exception.getStackTrace())
                .forEach(ste -> sb.append("    at ").append(ste.toString()).append("\n"));
        Throwable cause = exception.getCause();
        while (cause != null) {
            sb.append("Caused by: ").append(cause.toString()).append("\n");
            Arrays.stream(cause.getStackTrace())
                    .forEach(ste -> sb.append("    at ").append(ste.toString()).append("\n"));
            cause = cause.getCause();
        }
        return new ErrorDescription(sb.toString());
    }
}
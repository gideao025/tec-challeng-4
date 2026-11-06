package br.com.fiap.feedback.commons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ClassificadorUrgenciaTest {

    private final ClassificadorUrgencia classificador = new ClassificadorUrgencia(3, 6);

    @ParameterizedTest(name = "nota {0} -> {1}")
    @CsvSource({
        "0, CRITICA", "3, CRITICA",
        "4, MODERADA", "6, MODERADA",
        "7, BAIXA", "10, BAIXA"
    })
    void classificaTodasAsFronteiras(int nota, Urgencia esperada) {
        assertEquals(esperada, classificador.classificar(nota));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 11, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void rejeitaNotaForaDoIntervalo(int nota) {
        assertThrows(IllegalArgumentException.class, () -> classificador.classificar(nota));
    }

    @Test
    void limitesConfiguraveisMudamAsFaixas() {
        var outro = new ClassificadorUrgencia(4, 7);
        assertEquals(Urgencia.CRITICA, outro.classificar(4));
        assertEquals(Urgencia.MODERADA, outro.classificar(7));
        assertEquals(Urgencia.BAIXA, outro.classificar(8));
    }

    @Test
    void doAmbienteUsaPadroes3e6SemVariaveis() {
        var padrao = ClassificadorUrgencia.doAmbiente();
        assertEquals(Urgencia.CRITICA, padrao.classificar(3));
        assertEquals(Urgencia.MODERADA, padrao.classificar(6));
        assertEquals(Urgencia.BAIXA, padrao.classificar(7));
    }

    @ParameterizedTest
    @CsvSource({"-1, 6", "6, 6", "7, 6", "3, 10"})
    void rejeitaLimitesIncoerentes(int critica, int moderada) {
        assertThrows(IllegalArgumentException.class, () -> new ClassificadorUrgencia(critica, moderada));
    }
}

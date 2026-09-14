import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Representa os graus legais de insalubridade previstos na CLT (Art. 192).
 */
enum GrauInsalubridade {
    NENHUM(BigDecimal.ZERO),
    MINIMO(new BigDecimal("0.10")),   // 10%
    MEDIO(new BigDecimal("0.20")),    // 20%
    MAXIMO(new BigDecimal("0.40"));   // 40%

    private final BigDecimal percentual;

    GrauInsalubridade(BigDecimal percentual) {
        this.percentual = percentual;
    }

    public BigDecimal getPercentual() {
        return percentual;
    }
}

/**
 * Armazena os dados de uma competência mensal (mês/ano) de FGTS do trabalhador.
 */
class RegistroCompetenciaFgts {
    private final YearMonth competencia;
    private final BigDecimal baseCalculo;
    private final BigDecimal aliquotaFgts;
    private final BigDecimal valorDepositado;

    public RegistroCompetenciaFgts(YearMonth competencia, BigDecimal baseCalculo, BigDecimal aliquotaFgts) {
        this.competencia = Objects.requireNonNull(competencia, "Competência não pode ser nula");
        this.baseCalculo = Objects.requireNonNull(baseCalculo, "Base de cálculo não pode ser nula");
        this.aliquotaFgts = Objects.requireNonNull(aliquotaFgts, "Alíquota não pode ser nula");
        
        // Depósito = Base * Alíquota
        this.valorDepositado = baseCalculo.multiply(aliquotaFgts).setScale(2, RoundingMode.HALF_EVEN);
    }

    public YearMonth getCompetencia() {
        return competencia;
    }

    public BigDecimal getBaseCalculo() {
        return baseCalculo;
    }

    public BigDecimal getAliquotaFgts() {
        return aliquotaFgts;
    }

    public BigDecimal getValorDepositado() {
        return valorDepositado;
    }

    @Override
    public String toString() {
        return String.format("Competência: %s | Base: R$ %,.2f | FGTS (%s%%): R$ %,.2f",
                competencia, baseCalculo, aliquotaFgts.multiply(new BigDecimal("100")), valorDepositado);
    }
}

/**
 * Classe responsável por armazenar a conta vinculada e o histórico de competências trabalhadas.
 */
class ContaFgtsEmpregado {
    private final String matricula;
    private final String nomeEmpregado;
    private final List<RegistroCompetenciaFgts> historicoMeses;
    private static final BigDecimal ALIQUOTA_PADRAO = new BigDecimal("0.08"); // 8%

    public ContaFgtsEmpregado(String matricula, String nomeEmpregado) {
        this.matricula = matricula;
        this.nomeEmpregado = nomeEmpregado;
        this.historicoMeses = new ArrayList<>();
    }

    /**
     * Adiciona uma competência com a alíquota padrão CLT (8%).
     */
    public void creditarCompetencia(YearMonth competencia, BigDecimal remuneracaoTotal) {
        RegistroCompetenciaFgts registro = new RegistroCompetenciaFgts(competencia, remuneracaoTotal, ALIQUOTA_PADRAO);
        this.historicoMeses.add(registro);
    }

    /**
     * Retorna o saldo acumulado total sem juros/correções monetárias.
     */
    public BigDecimal calcularSaldoTotal() {
        return historicoMeses.stream()
                .map(RegistroCompetenciaFgts::getValorDepositado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int getTotalMesesTrabalhados() {
        return historicoMeses.size();
    }

    public List<RegistroCompetenciaFgts> getHistorico() {
        return Collections.unmodifiableList(historicoMeses);
    }

    public String getNomeEmpregado() {
        return nomeEmpregado;
    }

    public String getMatricula() {
        return matricula;
    }
}

/**
 * Calculadora de Verbas e Adicionais Trabalhistas segundo as diretrizes da CLT.
 */
class CalculadoraTrabalhista {

    // Fator de conversão da Hora Ficta Noturna (60 min / 52.5 min = 1.142857...)
    private static final BigDecimal FATOR_HORA_FICTA = new BigDecimal("60.0")
            .divide(new BigDecimal("52.5"), 8, RoundingMode.HALF_EVEN);

    /**
     * Calcula o valor da hora de trabalho comum.
     * Fórmula: Salário Base / Carga Horária Mensal (ex: 220h)
     */
    public static BigDecimal calcularValorHoraNormal(BigDecimal salarioBase, BigDecimal divisorHorasMensais) {
        return salarioBase.divide(divisorHorasMensais, 4, RoundingMode.HALF_EVEN);
    }

    /**
     * Calcula as Horas Extras.
     * Fórmula: (Valor da Hora Normal * (1 + Percentual)) * Quantidade de Horas
     */
    public static BigDecimal calcularHorasExtras(BigDecimal valorHoraNormal, BigDecimal quantidadeHoras, BigDecimal percentualAdicional) {
        BigDecimal multiplicador = BigDecimal.ONE.add(percentualAdicional);
        BigDecimal valorHoraExtra = valorHoraNormal.multiply(multiplicador);
        return valorHoraExtra.multiply(quantidadeHoras).setScale(2, RoundingMode.HALF_EVEN);
    }

    /**
     * Calcula o Adicional Noturno Urbano (CLT Art. 73).
     * Aplica o percentual de 20% somado ao efeito da redução ficta noturna.
     */
    public static BigDecimal calcularAdicionalNoturno(BigDecimal valorHoraNormal, BigDecimal horasNoturnasRelogio, BigDecimal percentualNoturno) {
        // Converte as horas de relógio para a quantidade computada (hora ficta)
        BigDecimal horasApuradas = horasNoturnasRelogio.multiply(FATOR_HORA_FICTA);
        // Valor do adicional = Hora Normal * Percentual * Horas Apuradas
        BigDecimal adicionalPorHora = valorHoraNormal.multiply(percentualNoturno);
        return adicionalPorHora.multiply(horasApuradas).setScale(2, RoundingMode.HALF_EVEN);
    }

    /**
     * Calcula o Adicional de Insalubridade (CLT Art. 192).
     * Fórmula: Salário Mínimo de Referência * Percentual do Grau
     */
    public static BigDecimal calcularInsalubridade(BigDecimal salarioMinimoReferencia, GrauInsalubridade grau) {
        return salarioMinimoReferencia.multiply(grau.getPercentual()).setScale(2, RoundingMode.HALF_EVEN);
    }
}

public class Main {
    public static void main(String[] args) {
        // Parâmetros de Entrada
        BigDecimal salarioBase = new BigDecimal("3500.00");
        BigDecimal divisorHoras = new BigDecimal("220");          // Jornada padrão de 44h semanais
        BigDecimal salarioMinimo = new BigDecimal("1412.00");     // Referência base para insalubridade

        System.out.println("=================================================");
        System.out.println("   DEMONSTRATIVO DE CÁLCULOS TRABALHISTAS");
        System.out.println("=================================================");

        // 1. Valor da Hora Normal
        BigDecimal valorHora = CalculadoraTrabalhista.calcularValorHoraNormal(salarioBase, divisorHoras);
        System.out.printf("Salário Base: R$ %,.2f | Valor da Hora Normal: R$ %,.2f%n", salarioBase, valorHora);

        // 2. Horas Extras (Exemplo: 15 horas extras com 50% de adicional)
        BigDecimal qtdHorasExtras = new BigDecimal("15");
        BigDecimal percHoraExtra = new BigDecimal("0.50"); // 50%
        BigDecimal totalHorasExtras = CalculadoraTrabalhista.calcularHorasExtras(valorHora, qtdHorasExtras, percHoraExtra);
        System.out.printf("Horas Extras (15h a 50%%): R$ %,.2f%n", totalHorasExtras);

        // 3. Adicional Noturno (Exemplo: 20 horas noturnas de relógio, 20% legal com hora ficta)
        BigDecimal qtdHorasNoturnas = new BigDecimal("20");
        BigDecimal percNoturno = new BigDecimal("0.20"); // 20%
        BigDecimal totalNoturno = CalculadoraTrabalhista.calcularAdicionalNoturno(valorHora, qtdHorasNoturnas, percNoturno);
        System.out.printf("Adicional Noturno (20h relógio c/ redução ficta): R$ %,.2f%n", totalNoturno);

        // 4. Insalubridade (Grau Médio: 20% sobre o Salário Mínimo)
        BigDecimal totalInsalubridade = CalculadoraTrabalhista.calcularInsalubridade(salarioMinimo, GrauInsalubridade.MEDIO);
        System.out.printf("Insalubridade Grau Médio (20%% s/ mínimo): R$ %,.2f%n", totalInsalubridade);

        // 5. Remuneração Bruta da Competência (Base para cálculo do FGTS)
        BigDecimal remuneracaoBruta = salarioBase
                .add(totalHorasExtras)
                .add(totalNoturno)
                .add(totalInsalubridade);
        System.out.printf("Remuneração Bruta do Mês: R$ %,.2f%n", remuneracaoBruta);

        System.out.println("\n=================================================");
        System.out.println("   HISTÓRICO DA CONTA VINCULADA DE FGTS");
        System.out.println("=================================================");

        ContaFgtsEmpregado contaFgts = new ContaFgtsEmpregado("MAT-9876", "Carlos Eduardo da Silva");

        // Simulando o lançamento de 3 competências
        contaFgts.creditarCompetencia(YearMonth.of(2026, 1), new BigDecimal("3500.00"));
        contaFgts.creditarCompetencia(YearMonth.of(2026, 2), new BigDecimal("3850.50"));
        contaFgts.creditarCompetencia(YearMonth.of(2026, 3), remuneracaoBruta);

        // Listagem dos meses trabalhados
        for (RegistroCompetenciaFgts reg : contaFgts.getHistorico()) {
            System.out.println(reg);
        }

        System.out.println("-------------------------------------------------");
        System.out.printf("Empregado: %s (Matrícula: %s)%n", contaFgts.getNomeEmpregado(), contaFgts.getMatricula());
        System.out.printf("Total de Meses Trabalhados: %d meses%n", contaFgts.getTotalMesesTrabalhados());
        System.out.printf("Saldo Total Acumulado do FGTS: R$ %,.2f%n", contaFgts.calcularSaldoTotal());
        System.out.println("=================================================");
    }
}

package com.minimal.launcher.apps;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A regra da busca: qual app responde a um texto digitado, e em que ordem.
 *
 * <p>Sem Android e sem estado, para a regra poder ser lida sem aparelho. A
 * comparacao acontece sobre o rotulo ja normalizado (minusculas, sem acento) e
 * o que define a ordem e a proximidade entre o texto e o nome do app:
 *
 * <ol>
 *   <li>nome igual ao texto;</li>
 *   <li>nome comeca com o texto;</li>
 *   <li>uma palavra do nome comeca com o texto;</li>
 *   <li>nome contem o texto;</li>
 *   <li>o texto aparece na ordem, pulando letras: "tk" acha "TikTok".</li>
 * </ol>
 *
 * <p>O ultimo caso e o que faz o enter funcionar com o texto pela metade. Ele
 * exige dois caracteres: com um so quase todo nome do aparelho casaria, e a
 * busca viraria loteria.
 *
 * <p>A tela mostra o primeiro e conta o resto. Por isso o retorno e a lista
 * inteira, e nao so o melhor.
 */
public final class AppSearch {

    /**
     * Acento decomposto e separado: "cafe" precisa casar com "Café".
     *
     * <p>Compilado uma vez. {@code String.replaceAll} compila o padrao a cada
     * chamada, e aqui a normalizacao roda para todo app instalado.
     */
    private static final Pattern DIACRITICS =
            Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    /** Ordem de preferencia: menor vence. */
    private static final int EXACT = 0;
    private static final int PREFIX = 1;
    private static final int WORD_PREFIX = 2;
    private static final int CONTAINS = 3;
    private static final int SUBSEQUENCE = 4;

    /** Abaixo disso a busca por subsequencia casa demais para valer algo. */
    private static final int MIN_SUBSEQUENCE_QUERY = 2;

    private static final Comparator<Match> BY_RELEVANCE = (left, right) -> {
        if (left.rank != right.rank) {
            return left.rank - right.rank;
        }
        // Empate de forca: importa onde o texto cai no nome, e nomes curtos
        // ganham de nomes longos porque tem menos onde o usuario se perdeu.
        if (left.position != right.position) {
            return left.position - right.position;
        }
        int byLength = left.entry.normalizedLabel.length()
                - right.entry.normalizedLabel.length();
        if (byLength != 0) {
            return byLength;
        }
        // Ultimo recurso, para a ordem nao depender de como a lista chegou.
        return left.entry.normalizedLabel.compareTo(right.entry.normalizedLabel);
    };

    /** Recebe o resultado, sempre na main thread. */
    public interface Callback {
        void onMatches(@NonNull List<AppEntry> matches);
    }

    private AppSearch() {
    }

    /** Minusculas e sem acento. O rotulo normalizado e calculado uma vez, na criacao do app. */
    @NonNull
    public static String normalize(@NonNull String value) {
        String lower = value.toLowerCase(Locale.getDefault());
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed).replaceAll("");
    }

    /**
     * Apps que respondem a {@code query}, do mais provavel para o menos.
     *
     * @param apps a lista do repositorio, ja em ordem alfabetica. O desempate
     *             final e uma comparacao simples de string, entao ele existe
     *             para a ordem ser sempre a mesma e nao para refinar a ordem
     *             que a lista ja traz.
     */
    @NonNull
    public static List<AppEntry> rank(@NonNull String query, @NonNull List<AppEntry> apps) {
        String needle = normalize(query);
        if (needle.isEmpty()) {
            return Collections.emptyList();
        }

        List<Match> matches = new ArrayList<>();
        for (AppEntry entry : apps) {
            Match match = match(entry, needle);
            if (match != null) {
                matches.add(match);
            }
        }
        Collections.sort(matches, BY_RELEVANCE);

        List<AppEntry> ranked = new ArrayList<>(matches.size());
        for (Match match : matches) {
            ranked.add(match.entry);
        }
        return ranked;
    }

    /** Como este app responde a este texto, ou null se nao responde. */
    @Nullable
    private static Match match(@NonNull AppEntry entry, @NonNull String needle) {
        String haystack = entry.normalizedLabel;

        if (haystack.equals(needle)) {
            return new Match(entry, EXACT, 0);
        }
        if (haystack.startsWith(needle)) {
            return new Match(entry, PREFIX, 0);
        }

        int at = haystack.indexOf(needle);
        if (at > 0 && startsWord(haystack, at)) {
            return new Match(entry, WORD_PREFIX, at);
        }
        if (at > 0) {
            return new Match(entry, CONTAINS, at);
        }

        if (needle.length() >= MIN_SUBSEQUENCE_QUERY) {
            int start = subsequenceStart(haystack, needle);
            if (start >= 0) {
                return new Match(entry, SUBSEQUENCE, start);
            }
        }
        return null;
    }

    /** A posicao comeca uma palavra? "google play" tem fronteira em "play". */
    private static boolean startsWord(@NonNull String haystack, int at) {
        return at == 0 || !Character.isLetterOrDigit(haystack.charAt(at - 1));
    }

    /** Posicao da primeira letra casada, se todas as letras do texto aparecerem na ordem. */
    private static int subsequenceStart(@NonNull String haystack, @NonNull String needle) {
        int first = -1;
        int cursor = 0;
        for (int i = 0; i < needle.length(); i++) {
            int at = haystack.indexOf(needle.charAt(i), cursor);
            if (at < 0) {
                return -1;
            }
            if (first < 0) {
                first = at;
            }
            cursor = at + 1;
        }
        return first;
    }

    /** Um app e o quanto ele casa com o texto. */
    private static final class Match {
        final AppEntry entry;
        final int rank;
        final int position;

        Match(AppEntry entry, int rank, int position) {
            this.entry = entry;
            this.rank = rank;
            this.position = position;
        }
    }
}
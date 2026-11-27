package com.vtuber.overlay;

/** Mensagens de erro em português simples, sem termos técnicos. Textos próprios do app passam sem mudança. */
final class Friendly {
    private Friendly() { }

    private static boolean technical(String m) {
        String l = m.toLowerCase();
        return l.contains("java.") || l.contains("android.") || l.contains("exception") || l.contains("null") || l.contains("errno")
            || l.contains("illegal") || l.contains("failed to") || l.contains("unable to") || l.contains("stack") || l.contains("kotlin");
    }

    /** Motivo curto para falhas de arquivo, imagem ou modelo (vem depois de "Não consegui ...: "). */
    static String why(Throwable t) {
        String m = t == null ? null : t.getMessage();
        String l = m == null ? "" : m.toLowerCase();
        if (t instanceof OutOfMemoryError || l.contains("outofmemory")) return "o arquivo é grande demais para o seu celular.";
        if (l.contains("enospc") || l.contains("no space")) return "o celular está sem espaço livre.";
        if (t instanceof java.io.FileNotFoundException || l.contains("enoent") || l.contains("no such file")) return "não encontrei o arquivo. Escolha de novo.";
        if (t instanceof SecurityException || l.contains("eacces") || l.contains("permission denied")) return "o app não tem permissão para abrir esse arquivo.";
        if (m == null || m.trim().isEmpty() || technical(m)) return "algo deu errado. Tente de novo.";
        return m;
    }

    /** Falha de conexão da Live (texto vindo da biblioteca de transmissão). */
    static String live(String raw) {
        String l = raw == null ? "" : raw.toLowerCase();
        if (l.contains("unknownhost") || l.contains("unable to resolve") || l.contains("no address") || l.contains("unreachable"))
            return "Sem internet. Confira a conexão do celular e tente de novo.";
        if (l.contains("timeout") || l.contains("timed out"))
            return "O YouTube demorou demais para responder. Confira a internet e tente de novo.";
        if (l.contains("endpoint malformed"))
            return "O endereço do servidor da live está inválido. Confira o endereço.";
        if (l.contains("refused") || l.contains("reset") || l.contains("broken pipe") || l.contains("closed"))
            return "A conexão com o YouTube foi recusada ou interrompida. Confira a internet e a chave da live.";
        return "Não consegui conectar à live. Confira a internet, o endereço e a chave.";
    }

    /** Falha ao iniciar a Live. */
    static String liveStart(Throwable t) {
        if (t instanceof SecurityException) return "O Android não permitiu capturar a tela. Aceite o aviso de captura de tela e tente de novo.";
        return live(t == null ? null : t.getMessage());
    }
}

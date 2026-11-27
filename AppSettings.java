package com.vtuber.overlay;

import android.content.Context;
import android.content.SharedPreferences;

/** Configurações do app (tela "Configurações do app") e o estado da sala atual. */
final class AppSettings {
    private AppSettings() { }

    static final String[] ROOM_IDS = {"wait", "talk", "normal", "end"};
    static final String[] ROOM_NAMES = {"Sala de espera", "Sala de conversa", "Sala normal", "Sala de terminar"};

    static SharedPreferences p(Context c) { return c.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    static SharedPreferences main(Context c) { return c.getSharedPreferences("vtuber", Context.MODE_PRIVATE); }

    static boolean sideButtons(Context c) { return p(c).getBoolean("side_buttons", true); }
    static boolean waitingOnLive(Context c) { return p(c).getBoolean("waiting_on_live", true); }
    static boolean hideInWaitEnd(Context c) { return p(c).getBoolean("hide_wait_end", true); }
    /** 0 nenhuma | 1 suave | 2, 3, 4 = vídeo 1, 2, 3 */
    static int transition(Context c) { return p(c).getInt("transition", 1); }
    static boolean onboardingDone(Context c) { return p(c).getBoolean("onboarding_done", false); }
    static boolean stun(Context c) { return c.getSharedPreferences("collab", Context.MODE_PRIVATE).getBoolean("stun", true); }

    static String room(Context c) {
        String r = main(c).getString("room", "normal");
        for (String x : ROOM_IDS) if (x.equals(r)) return r;
        return "normal";
    }
    static void setRoom(Context c, String id) { main(c).edit().putString("room", id).apply(); }
    static String roomName(String id) {
        for (int i = 0; i < ROOM_IDS.length; i++) if (ROOM_IDS[i].equals(id)) return ROOM_NAMES[i];
        return ROOM_NAMES[2];
    }
    /** O Vtuber fica escondido na sala de espera e na tela final (se a opção estiver ligada). */
    static boolean hideModelNow(Context c) {
        String r = room(c);
        return hideInWaitEnd(c) && (r.equals("wait") || r.equals("end"));
    }
}

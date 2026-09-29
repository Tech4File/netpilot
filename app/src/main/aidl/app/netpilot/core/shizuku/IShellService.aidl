package app.netpilot.core.shizuku;

/** Runs inside the Shizuku server process (ADB-level privileges). */
interface IShellService {
    int run(in String[] command);
}

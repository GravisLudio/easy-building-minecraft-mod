package marum.easybuilding.gametest;

import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import marum.easybuilding.client.PlaneMode;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

/**
 * Real client test for the 26.2 port: opens Minecraft, builds a platform in the sky, turns Plane Mode on with the
 * real key (Left Alt), holds the use key and checks on the SERVER that the blocks really appeared where the plane
 * says. Also takes screenshots of the grid (horizontal, out of range = red, vertical).
 *
 * Run: {@code gradlew runClientGameTest --offline}. Screenshots: {@code build/run/clientGameTest/screenshots}.
 * Look for the {@code [EB-TEST]} markers in the output (the build ends in FAILED because of the shutdown watchdog).
 */
public class EasyBuildingClientTests implements FabricClientGameTest {

    private static final double EYE = 1.62;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            TestServerContext server = singleplayer.getServer();
            context.waitTicks(40);
            context.getInput().resizeWindow(1280, 760);

            server.runCommand("gamemode survival @p");
            server.runCommand("difficulty peaceful");
            server.runCommand("time set noon");
            server.runCommand("fill -8 198 -8 8 215 12 air");
            server.runCommand("fill -6 199 -6 6 199 0 stone");          // floor, z -6..0
            server.runCommand("fill -2 200 6 0 202 6 stone");           // small wall at z=6, x -2..0
            server.runCommand("clear @p");
            server.runCommand("give @p stone 64");
            context.waitTicks(10);

            // ---------- 1) Horizontal plane (UP face): select the floor, then extend it over the void ----------
            stand(server, 0.5, -1.5, aim(new Vec3(0.5, 200 + EYE, -1.5), new Vec3(0.5, 200, 0.5)));
            context.waitTicks(10);
            context.getInput().pressKey(GLFW.GLFW_KEY_LEFT_ALT);
            context.waitTicks(5);
            check(clientBool(context, mc -> PlaneMode.active), "Plane mode did not turn on with Left Alt");
            check(clientBool(context, mc -> PlaneMode.selectedFace == Direction.UP), "selected face is not UP");
            check(clientBool(context, mc -> new BlockPos(0, 199, 0).equals(PlaneMode.selectedBlockPos)),
                    "selected block is not (0,199,0)");
            System.out.println("[EB-TEST] 1a plane mode ON, face UP, block (0,199,0): OK");
            context.takeScreenshot("1-horizontal-plane-in-range");

            // Out of range: aim far away (z=5.5, 7 blocks): must NOT place anything, and the grid turns red
            stand(server, 0.5, -1.5, aim(new Vec3(0.5, 200 + EYE, -1.5), new Vec3(0.5, 200, 5.5)));
            context.waitTicks(10);
            context.takeScreenshot("2-horizontal-plane-out-of-range-red");
            context.getInput().holdKeyFor(o -> o.keyUse, 8);
            context.waitTicks(5);
            check(!blockIsStone(server, 0, 199, 5), "a block was placed OUT OF RANGE at (0,199,5)");
            System.out.println("[EB-TEST] 1b out of range: nothing placed: OK");

            // Drag: z=1,2,3 one by one (each aim hits the plane at z+0.5, 3 blocks from the player)
            for (int z = 1; z <= 3; z++) {
                double px = 0.5, pz = z - 3.0 + 0.5;
                stand(server, px, pz, aim(new Vec3(px, 200 + EYE, pz), new Vec3(0.5, 200, z + 0.5)));
                context.waitTicks(10);
                context.getInput().holdKeyFor(o -> o.keyUse, 8);
                context.waitTicks(8);
                final int zz = z;
                check(blockIsStone(server, 0, 199, zz), "block (0,199," + zz + ") was not placed on the horizontal plane");
                System.out.println("[EB-TEST] 1c placed (0,199," + z + "): OK");
            }
            check(!blockIsStone(server, 1, 199, 1), "a block appeared at (1,199,1), which was never aimed at");
            int left = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getInventory().countItem(Items.STONE));
            check(left == 61, "stone left should be 61 (3 placed), is " + left);
            System.out.println("[EB-TEST] 1d inventory 64 -> " + left + " (3 consumed): OK");
            context.takeScreenshot("3-horizontal-plane-after-drag");

            // Regression for upstream 1.0.1 ("Fixed crash when looking up or down"): with a HORIZONTAL plane,
            // looking exactly level makes the view ray parallel to the plane -> getProjectedBlock() == null.
            // The grid must just not be drawn (no crash) and holding use must place nothing.
            stand(server, 0.5, -1.5, new float[] { 0f, 0f });
            context.waitTicks(20);
            context.getInput().holdKeyFor(o -> o.keyUse, 8);
            context.waitTicks(8);
            check(clientBool(context, mc -> mc.level != null && mc.player != null), "client died with a parallel ray (pitch 0)");
            check(countStone(server) == 61, "stone count changed with a parallel ray, is " + countStone(server));
            System.out.println("[EB-TEST] 1f horizontal plane, pitch 0 (parallel ray): no crash, nothing placed: OK");

            // Left Alt again turns it off
            context.getInput().pressKey(GLFW.GLFW_KEY_LEFT_ALT);
            context.waitTicks(5);
            check(!clientBool(context, mc -> PlaneMode.active), "Plane mode did not turn off with Left Alt");
            System.out.println("[EB-TEST] 1e plane mode OFF with Alt: OK");

            // ---------- 2) Vertical plane (NORTH face of a wall at z=6): extend the wall sideways ----------
            stand(server, 0.5, 3.5, aim(new Vec3(0.5, 200 + EYE, 3.5), new Vec3(0.5, 200.5, 6.0)));
            context.waitTicks(10);
            context.getInput().pressKey(GLFW.GLFW_KEY_LEFT_ALT);
            context.waitTicks(5);
            check(clientBool(context, mc -> PlaneMode.active), "vertical: Plane mode did not turn on");
            check(clientBool(context, mc -> PlaneMode.selectedFace == Direction.NORTH), "vertical: selected face is not NORTH");
            check(clientBool(context, mc -> new BlockPos(0, 200, 6).equals(PlaneMode.selectedBlockPos)),
                    "vertical: selected block is not (0,200,6)");
            System.out.println("[EB-TEST] 2a vertical plane ON, face NORTH, block (0,200,6): OK");

            stand(server, 0.5, 3.5, aim(new Vec3(0.5, 200 + EYE, 3.5), new Vec3(1.5, 200.5, 6.0)));
            context.waitTicks(10);
            context.takeScreenshot("4-vertical-plane");
            context.getInput().holdKeyFor(o -> o.keyUse, 8);
            context.waitTicks(8);
            check(blockIsStone(server, 1, 200, 6), "vertical: block (1,200,6) was not placed");
            System.out.println("[EB-TEST] 2b placed (1,200,6) on the vertical plane: OK");

            // (1,201,6) touches the wall block (0,201,6): placed upwards. The mod, like the original, only places
            // against an adjacent face, so a spot with only diagonal neighbours (2,201,6) must stay empty.
            stand(server, 0.5, 3.5, aim(new Vec3(0.5, 200 + EYE, 3.5), new Vec3(1.5, 201.5, 6.0)));
            context.waitTicks(10);
            context.getInput().holdKeyFor(o -> o.keyUse, 8);
            context.waitTicks(8);
            check(blockIsStone(server, 1, 201, 6), "vertical: block (1,201,6) was not placed");
            System.out.println("[EB-TEST] 2c placed (1,201,6) on the vertical plane: OK");

            stand(server, 0.5, 3.5, aim(new Vec3(0.5, 200 + EYE, 3.5), new Vec3(3.5, 202.5, 6.0)));
            context.waitTicks(10);
            context.getInput().holdKeyFor(o -> o.keyUse, 8);
            context.waitTicks(8);
            check(!blockIsStone(server, 3, 202, 6), "vertical: (3,202,6) has no adjacent face and must stay empty");
            System.out.println("[EB-TEST] 2d no adjacent face -> nothing placed: OK");
            context.takeScreenshot("5-vertical-plane-after-drag");

            // Regression for upstream 1.0.1 again, this time with a VERTICAL plane: looking straight up / down.
            int stoneBefore = countStone(server);
            for (float pitch : new float[] { -90f, 90f }) {
                stand(server, 0.5, 3.5, new float[] { 0f, pitch });
                context.waitTicks(20);
                context.getInput().holdKeyFor(o -> o.keyUse, 8);
                context.waitTicks(8);
                check(clientBool(context, mc -> mc.level != null && mc.player != null), "client died looking at pitch " + pitch);
                check(countStone(server) == stoneBefore, "stone count changed looking at pitch " + pitch);
            }
            System.out.println("[EB-TEST] 2e vertical plane, looking straight up and down: no crash, nothing placed: OK");

            context.getInput().pressKey(GLFW.GLFW_KEY_LEFT_ALT);
            context.waitTicks(5);
            check(!clientBool(context, mc -> PlaneMode.active), "vertical: Plane mode did not turn off");

            // ---------- 3) Slabs keep their half: bottom slab floor -> bottom slabs, top slab floor -> top slabs ----------
            server.runCommand("clear @p");
            server.runCommand("give @p stone_slab 64");
            for (SlabType half : new SlabType[] { SlabType.BOTTOM, SlabType.TOP }) {
                String type = half == SlabType.BOTTOM ? "bottom" : "top";
                double standY = half == SlabType.BOTTOM ? 199.5 : 200.0;   // top of the slab layer
                server.runCommand("fill -6 199 7 6 199 14 air");
                server.runCommand("fill -6 199 7 6 199 10 stone_slab[type=" + type + "]");
                context.waitTicks(10);
                // Select the slab floor from its top face
                stand(server, 0.5, standY, 8.5, aim(new Vec3(0.5, standY + EYE, 8.5), new Vec3(0.5, standY, 9.5)));
                context.waitTicks(10);
                context.getInput().pressKey(GLFW.GLFW_KEY_LEFT_ALT);
                context.waitTicks(5);
                check(clientBool(context, mc -> PlaneMode.active), "slab " + type + ": Plane mode did not turn on");
                // Extend one block past the edge (z=11): aim at the plane, 3 blocks away
                stand(server, 0.5, standY, 8.5, aim(new Vec3(0.5, standY + EYE, 8.5), new Vec3(0.5, standY, 11.5)));
                context.waitTicks(10);
                context.takeScreenshot("6-slab-" + type + "-plane");
                context.getInput().holdKeyFor(o -> o.keyUse, 8);
                context.waitTicks(8);
                SlabType placed = slabTypeAt(server, 0, 199, 11);
                check(placed == half, "slab " + type + ": block at (0,199,11) is " + placed + ", expected " + half);
                System.out.println("[EB-TEST] 3 " + type + " slab floor extended with a " + placed + " slab: OK");
                context.getInput().pressKey(GLFW.GLFW_KEY_LEFT_ALT);
                context.waitTicks(5);
                check(!clientBool(context, mc -> PlaneMode.active), "slab " + type + ": Plane mode did not turn off");
            }

            // ---------- 4) Leaving the world with the mode still ON (crash reported by Gravis on 2026-10-15) ----------
            // Easy Building keeps its state in statics. After "Save and Quit" the client has no player/level any more;
            // the tick handler used to call getProjectedBlock() -> NullPointerException on client.player.
            server.runCommand("fill -6 199 7 6 199 14 air");
            server.runCommand("fill -6 199 7 6 199 10 stone");
            context.waitTicks(10);
            stand(server, 0.5, 8.5, aim(new Vec3(0.5, 200 + EYE, 8.5), new Vec3(0.5, 200, 9.5)));
            context.waitTicks(10);
            context.getInput().pressKey(GLFW.GLFW_KEY_LEFT_ALT);
            context.waitTicks(5);
            check(clientBool(context, mc -> PlaneMode.active), "leave-world: Plane mode did not turn on");

            // Other lifecycle changes with the mode ON: dimension change, then death + respawn (new LocalPlayer).
            server.runCommand("gamemode creative @p");
            server.runCommand("execute in minecraft:the_nether run tp @p 0 120 0");
            context.waitTicks(60);
            server.runCommand("execute in minecraft:overworld run tp @p 0.5 200 8.5 0 40");
            context.waitTicks(40);
            check(clientBool(context, mc -> mc.level != null && mc.player != null), "client died after a dimension change");
            System.out.println("[EB-TEST] 4d dimension change with the mode ON: no crash: OK");

            server.runCommand("gamemode survival @p");
            server.runCommand("kill @p");
            context.waitForScreen(DeathScreen.class);
            context.clickScreenButton("deathScreen.respawn");
            context.waitTicks(60);
            check(clientBool(context, mc -> mc.level != null && mc.player != null && mc.player.isAlive()), "client died after respawn");
            System.out.println("[EB-TEST] 4e death + respawn with the mode ON: no crash: OK");

            // Make sure the mode is ON again (whatever the respawn did) before leaving the world
            server.runCommand("tp @p 0.5 200 8.5 0 40");
            context.waitTicks(20);
            if (!clientBool(context, mc -> PlaneMode.active)) {
                stand(server, 0.5, 8.5, aim(new Vec3(0.5, 200 + EYE, 8.5), new Vec3(0.5, 200, 9.5)));
                context.waitTicks(10);
                context.getInput().pressKey(GLFW.GLFW_KEY_LEFT_ALT);
                context.waitTicks(5);
            }
            check(clientBool(context, mc -> PlaneMode.active), "leave-world: Plane mode is not ON before leaving");
            System.out.println("[EB-TEST] 4a plane mode ON, leaving the world now");
        }   // <- closes the singleplayer world (the client returns to the title screen)

        context.waitTicks(40);
        check(clientBool(context, mc -> mc.level == null && mc.player == null), "still in a world after closing it");
        check(!clientBool(context, mc -> PlaneMode.active), "Plane mode is still ON after leaving the world");
        System.out.println("[EB-TEST] 4b left the world with the mode ON: no crash, state cleared: OK");

        // Alt on the title screen (no player, no level) must not crash either
        context.getInput().pressKey(GLFW.GLFW_KEY_LEFT_ALT);
        context.waitTicks(20);
        check(clientBool(context, mc -> mc.player == null), "title screen: unexpected player");
        check(!clientBool(context, mc -> PlaneMode.active), "Plane mode turned ON at the title screen");
        System.out.println("[EB-TEST] 4c Alt on the title screen: no crash, mode stays off: OK");
        System.out.println("[EB-TEST] ALL OK");
    }

    /** Teleports the player to the given spot of the platform (y=200) with the given {yaw, pitch}. */
    private static void stand(TestServerContext server, double x, double z, float[] yawPitch) {
        stand(server, x, 200.0, z, yawPitch);
    }

    private static void stand(TestServerContext server, double x, double y, double z, float[] yawPitch) {
        server.runCommand(String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.4f %.4f", x, y, z, yawPitch[0], yawPitch[1]));
    }

    private static int countStone(TestServerContext server) {
        return server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getInventory().countItem(Items.STONE));
    }

    /** Slab half at the position, or null if it is not a stone slab. */
    private static SlabType slabTypeAt(TestServerContext server, int x, int y, int z) {
        return server.computeOnServer(s -> {
            BlockState state = s.overworld().getBlockState(new BlockPos(x, y, z));
            return state.is(Blocks.STONE_SLAB) ? state.getValue(SlabBlock.TYPE) : null;
        });
    }

    /** Yaw/pitch (Minecraft convention) to look from {@code eye} to {@code target}. */
    private static float[] aim(Vec3 eye, Vec3 target) {
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
        return new float[] { yaw, pitch };
    }

    private static boolean blockIsStone(TestServerContext server, int x, int y, int z) {
        return server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(x, y, z)).is(Blocks.STONE));
    }

    private static boolean clientBool(ClientGameTestContext context, java.util.function.Predicate<net.minecraft.client.Minecraft> p) {
        return context.computeOnClient(mc -> p.test(mc));
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            System.out.println("[EB-TEST] FAIL: " + message);
            throw new AssertionError(message);
        }
    }
}

package marum.easybuilding.gametest;

import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import marum.easybuilding.client.PlaneMode;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
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

            context.getInput().pressKey(GLFW.GLFW_KEY_LEFT_ALT);
            context.waitTicks(5);
            check(!clientBool(context, mc -> PlaneMode.active), "vertical: Plane mode did not turn off");
            System.out.println("[EB-TEST] ALL OK");
        }
    }

    /** Teleports the player to the given spot of the platform with the given {yaw, pitch}. */
    private static void stand(TestServerContext server, double x, double z, float[] yawPitch) {
        server.runCommand(String.format(Locale.ROOT, "tp @p %.3f 200 %.3f %.4f %.4f", x, z, yawPitch[0], yawPitch[1]));
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

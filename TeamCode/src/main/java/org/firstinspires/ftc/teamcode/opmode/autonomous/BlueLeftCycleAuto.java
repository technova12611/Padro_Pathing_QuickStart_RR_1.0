package org.firstinspires.ftc.teamcode.opmode.autonomous;

import com.acmerobotics.dashboard.config.Config;
import com.acmerobotics.roadrunner.Pose2d;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;

import org.firstinspires.ftc.teamcode.pipeline.AlliancePosition;

@Config
@Autonomous(name = "Blue Near Cycle Auto (2+4)", group = "BLUE Auto", preselectTeleOp = "Manual Drive")
public class BlueLeftCycleAuto extends NearCycleAutoBase {

    @Override
    protected void onInit() {
        this.start = BlueLeftAuto.blue_start;

        this.backdrop = BlueLeftAuto.blue_backdrop;
        this.spike = BlueLeftAuto.blue_spike;

        this.cycleStart = new Pose2d[]{
                new Pose2d(spike[0].position.x, 12.0, Math.toRadians(-180)),
                new Pose2d(spike[1].position.x, 12.0, Math.toRadians(-180)),
                new Pose2d(spike[2].position.x, 12.0, Math.toRadians(-180))
        };

        this.stackAlignment = new Pose2d(-49.0, 12.0, Math.toRadians(-180));
        this.stackIntake1 = new Pose2d(-55.7, 11.8, Math.toRadians(-180));
        this.stackIntake2 = new Pose2d(-56.3, 12.8, Math.toRadians(-180));
        this.safeTrussPassStop = new Pose2d(-49.0, 12.3, Math.toRadians(-180));

        this.backdropAlignment = new Pose2d(45.0, 12.0, Math.toRadians(-180));

        this.cycleScore = new Pose2d[]{
                new Pose2d(47.5, 33.0, Math.toRadians(-180)),
                new Pose2d(47.5, 31.0, Math.toRadians(-180)),
                new Pose2d(47.5, 38.0, Math.toRadians(-180))
        };

        this.parking = new Pose2d(45.0, 20.0, Math.toRadians(-180));
    }

    protected AlliancePosition getAlliance() {
        return AlliancePosition.BLUE;
    }

    @Override
    protected void printDescription() {
        telemetry.addData("Description", "BLUE Left Cycle Auto");
    }

}

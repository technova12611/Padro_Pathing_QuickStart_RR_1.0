package org.firstinspires.ftc.teamcode.opmode.teleops;

import android.util.Log;

import androidx.annotation.NonNull;

import com.acmerobotics.dashboard.config.Config;
import com.acmerobotics.roadrunner.AccelConstraint;
import com.acmerobotics.roadrunner.AngularVelConstraint;
import com.acmerobotics.roadrunner.Arclength;
import com.acmerobotics.roadrunner.MinVelConstraint;
import com.acmerobotics.roadrunner.Pose2dDual;
import com.acmerobotics.roadrunner.PosePath;
import com.acmerobotics.roadrunner.PoseVelocity2d;
import com.acmerobotics.roadrunner.ProfileAccelConstraint;
import com.acmerobotics.roadrunner.SequentialAction;
import com.acmerobotics.roadrunner.SleepAction;
import com.acmerobotics.roadrunner.Vector2d;
import com.acmerobotics.roadrunner.VelConstraint;
import com.acmerobotics.roadrunner.ftc.Actions;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import org.firstinspires.ftc.teamcode.Globals;
import org.firstinspires.ftc.teamcode.MecanumDrive;
import org.firstinspires.ftc.teamcode.roadrunner.PoseMessage;
import org.firstinspires.ftc.teamcode.subsystem.Hang;
import org.firstinspires.ftc.teamcode.subsystem.Intake;
import org.firstinspires.ftc.teamcode.subsystem.Memory;
import org.firstinspires.ftc.teamcode.subsystem.Outtake;
import org.firstinspires.ftc.teamcode.subsystem.Drone;
import org.firstinspires.ftc.teamcode.utils.software.ActionScheduler;
import org.firstinspires.ftc.teamcode.utils.hardware.GamePadController;
import org.firstinspires.ftc.teamcode.utils.software.AutoActionScheduler;
import org.firstinspires.ftc.teamcode.utils.software.SmartGameTimer;

import java.util.Arrays;

@Config
@TeleOp(group = "Drive", name="Manual Drive")
public class ManualDrive extends LinearOpMode {
    public static double TURN_SPEED = 0.75;
    public static double DRIVE_SPEED = 1;
    public static double SLOW_TURN_SPEED = 0.3;
    public static double SLOW_DRIVE_SPEED = 0.3;

    public static double STRAFE_DISTANCE = 3.0;

    private SmartGameTimer smartGameTimer;
    private GamePadController g1, g2;
    private MecanumDrive drive;
    private ActionScheduler sched;

    private AutoActionScheduler autoRunSched;
    private Intake intake;
    private Outtake outtake;
    private Hang hang;
    private Drone drone;

    Long intakeReverseStartTime = null;

    Long intakeSlowdownStartTime = null;

    Long lastTimePixelDetected = null;
    boolean isPixelDetectionEnabled = true;

    int prevPixelCount = 0;

    VelConstraint velConstraintOverride;
    AccelConstraint accelConstraintOverride = new ProfileAccelConstraint(-30.0, 30.0);

    @Override
    public void runOpMode() throws InterruptedException {
        telemetry.addLine("Initializing...");
        telemetry.update();

        // Init
        g1 = new GamePadController(gamepad1);
        g2 = new GamePadController(gamepad2);
        g1.update();
        g2.update();
        sched = new ActionScheduler();
        drive = new MecanumDrive(hardwareMap, Memory.LAST_POSE);
        intake = new Intake(hardwareMap);
        outtake = new Outtake(hardwareMap);
        hang = new Hang(hardwareMap);
        drone = new Drone(hardwareMap);

        smartGameTimer = new SmartGameTimer(false);

        if(Globals.RUN_AUTO) {
            drive.pose = Globals.drivePose;
        }
        outtake.isAuto = false;
        Globals.RUN_AUTO = false;
        outtake.prepTeleop();

        intake.initialize(false);
        // Init opmodes
        outtake.initialize();
        drone.initialize();
        hang.initialize();

        // Ready!
        telemetry.addLine("Manual Drive is Ready!");
        telemetry.addLine("Drive Pose: " + new PoseMessage(drive.pose));
        telemetry.update();

        velConstraintOverride = new MinVelConstraint(Arrays.asList(
                this.drive.kinematics.new WheelVelConstraint(45.0),
                new AngularVelConstraint(Math.PI/2)));

        waitForStart();

        // Opmode start
        if (opModeIsActive()) {
            resetRuntime();
            g1.reset();
            g2.reset();

            // Finish pulling in slides
            if (!smartGameTimer.isNominal()) {
                outtake.finishPrepTeleop();
            }
        }

        // Main loop
        while (opModeIsActive()) {
            g1.update();
            g2.update();

            move();
            subsystemControls();
            pixelDetection();

            drive.updatePoseEstimate();
            sched.update();
            outtake.update();
            intake.update();

            telemetry.addData("Time left: ", smartGameTimer.formattedString() + " (" + smartGameTimer.status() + ")");
            telemetry.addLine(intake.getStackServoPositions());
            telemetry.addLine(outtake.getServoPositions());
            telemetry.addLine("Current Pixel count: " + Intake.pixelsCount + " | Total count: " + Intake.totalPixelCount + " | Prev count: " + prevPixelCount);
            telemetry.addLine("Intake state: " + intake.intakeState);
            telemetry.addLine(hang.getCurrentPosition());
            telemetry.update();
        }

        // On termination
        Memory.LAST_POSE = drive.pose;
    }

    private void pixelDetection() {
        if (isPixelDetectionEnabled) {

            // if the 2nd pixel is too close to the 1st, reverse intake for a moment
            if (prevPixelCount != Intake.pixelsCount && lastTimePixelDetected != null &&
                    (System.currentTimeMillis() - lastTimePixelDetected.longValue()) < 600) {

                if (Intake.pixelsCount == 2) {
                    Intake.totalPixelCount--;
                    Intake.pixelsCount = 1;

                    Log.d("TeleOps_Pixel_detection", "deduct 1 from the counts. Total: " + Intake.totalPixelCount + " current: " + Intake.pixelsCount);
                }
                sched.queueAction(intake.intakeReverse());

                Log.d("TeleOps_Pixel_detection", "slow down 2nd pixel at " + System.currentTimeMillis());

                lastTimePixelDetected = null;
                intakeSlowdownStartTime = new Long(System.currentTimeMillis());
            }
            // log the count
            else if (prevPixelCount != Intake.pixelsCount && Intake.pixelsCount >= 2 && intakeReverseStartTime == null) {
                intakeReverseStartTime = new Long(System.currentTimeMillis());

                Log.d("TeleOps_Pixel_detection", "Pixel counted changed 1 -> 2, detected at :" + intakeReverseStartTime);
            }

            if (intakeSlowdownStartTime != null) {
                if ((System.currentTimeMillis() - intakeSlowdownStartTime.longValue()) > 300) {
                    sched.queueAction(intake.intakeOn());
                    intakeSlowdownStartTime = null;
                }
            }

            // intake reverse is on
            if (intakeReverseStartTime != null && intakeSlowdownStartTime == null) {

                if ((System.currentTimeMillis() - intakeReverseStartTime.longValue()) > 500
                        && (System.currentTimeMillis() - intakeReverseStartTime.longValue() < 600)) {
                    sched.queueAction(intake.intakeReverse());
                    Log.d("TeleOps_Pixel_detection", "Pixel count 1 -> 2, reverse started at" + System.currentTimeMillis());
                }

                if ((System.currentTimeMillis() - intakeReverseStartTime.longValue()) > 1800) {
                    sched.queueAction(intake.intakeOff());
                    intakeReverseStartTime = null;

                    Log.d("TeleOps_Pixel_detection", "Intake reverse is completed.");
                }
            }

            // detect the first pixel in time
            if (Intake.pixelsCount == 1 && prevPixelCount == 0) {
                if(intake.stackIntakeState == Intake.StackIntakeState.UP && lastTimePixelDetected == null) {
                    lastTimePixelDetected = new Long(System.currentTimeMillis());
                }
                Log.d("TeleOps_Pixel_detection", "Pixel count changed 0 -> 1. Detected at " + lastTimePixelDetected);
            }

            prevPixelCount = Intake.pixelsCount;
        }
    }

    private void move() {
        double speed = (1-Math.abs(g1.right_stick_x)) * (DRIVE_SPEED - SLOW_DRIVE_SPEED) + SLOW_DRIVE_SPEED;
        double input_x = Math.pow(-g1.left_stick_y, 3) * speed;
        double input_y = Math.pow(-g1.left_stick_x, 3) * speed;

        double input_turn = Math.pow(g1.left_trigger - g1.right_trigger, 3) * TURN_SPEED;
        if (g1.leftBumper()) input_turn += SLOW_TURN_SPEED;
        if (g1.rightBumper()) input_turn -= SLOW_TURN_SPEED;

        Vector2d input = new Vector2d(input_x, input_y);
        drive.setDrivePowers(new PoseVelocity2d(input, input_turn));
    }

    boolean isSlideOut = false;
    boolean isStackIntakeOn = false;
    private void subsystemControls() {
        // Intake controls
        //
        //   xOnce for toggle intake ON/OFF
        //
        if(g1.aLong()) {
            isPixelDetectionEnabled = false;
        }
        else if (!g1.start() && g1.xOnce()) {
            if (intake.intakeState == Intake.IntakeState.ON) {
                sched.queueAction(intake.intakeOff());
            } else {
                Intake.pixelsCount = 0;
                sched.queueAction(new SequentialAction(
                        intake.intakeOn(),
                        outtake.prepareToTransfer()));
            }
        }

        if (g1.b()) {
            if (isSlideOut) {
                isSlideOut = false;
                Actions.runBlocking(outtake.retractOuttake());
            }
            sched.queueAction(intake.intakeReverse());
        }
        if (!g1.b() && intake.intakeState == Intake.IntakeState.REVERSING && intakeReverseStartTime == null) {
            sched.queueAction(intake.intakeOff());
        }

        // Outtake controls
        if(g1.yLong()) {
            sched.queueAction(outtake.latchScore2());
        }
        else if (g1.yOnce()) {
            if (isSlideOut) {
                if(outtake.latchState == Outtake.OuttakeLatchState.LATCH_1) {
                    sched.queueAction(outtake.latchScore2());
                }
                else {
                    sched.queueAction(outtake.latchScore1());
                }
            } else {
                isSlideOut = true;
                Actions.runBlocking(new SequentialAction(
                        intake.intakeOff(),
                        outtake.prepareToSlide()));

                sched.queueAction(outtake.extendOuttakeTeleOps());
                sched.queueAction(new SleepAction(0.5));
                sched.queueAction(outtake.prepareToScore());
            }
        }

        if (Math.abs(g1.right_stick_y) > 0.25  && isSlideOut) {
            sched.queueAction(outtake.moveSliderBlocking(-g1.right_stick_y));
        }

        // Hang arms up/down
        if (g1.dpadUpOnce()) {
            sched.queueAction(outtake.outtakeWireForHanging());
 //           sched.queueAction(hang.hookUp());
        }

        if(g1.dpadDownLong()) {
            sched.queueAction(hang.hang());
        }
        else if (g1.dpadDownOnce()) {
            sched.queueAction(hang.hangSlowly());
        }

        // move left and right by one slot
        if (g1.dpadLeftOnce() || g1.dpadRightOnce()) {

            int multiplier = (Globals.RUN_AUTO)?1:-1;

            double strafeDistance = (g1.dpadLeftOnce()? STRAFE_DISTANCE:-STRAFE_DISTANCE)*multiplier;

            autoRunSched = new AutoActionScheduler(this::update);
            Log.d("ManualDrive", "Pose before strafe: " + new PoseMessage(this.drive.pose) + " | target=" + strafeDistance);
            double start_y = drive.pose.position.y;
            autoRunSched.addAction(drive.actionBuilder(drive.pose)
                    .strafeTo(new Vector2d(drive.pose.position.x, drive.pose.position.y + strafeDistance),
                            velConstraintOverride,
                            accelConstraintOverride)
                    .build());

            autoRunSched.run();

            Log.d("ManualDrive", "Pose after strafe: " + new PoseMessage(this.drive.pose) + " | actual=" + (drive.pose.position.y-start_y));
        }

        // drone launch
        if (g1.backOnce()) {
            sched.queueAction( intake.stackIntakeLinkageDown());
            sched.queueAction( new SleepAction(0.25));
            sched.queueAction( drone.scoreDrone());
            sched.queueAction( new SleepAction(0.25));
            sched.queueAction( intake.stackIntakeLinkageUp());
        }

        // stack intake
        if(g1.start() && g1.x()) {
            if(!isStackIntakeOn) {
                isStackIntakeOn = true;
                sched.queueAction(intake.intakeStackedPixels());
            }
        }
        else if(g1.aOnce()) {
            isStackIntakeOn = false;
            if(intake.stackIntakeState == Intake.StackIntakeState.DOWN) {
                sched.queueAction(new SequentialAction(
                        intake.prepareTeleOpsIntake(),
                        intake.intakeOff()));
            } else {
                sched.queueAction(
                        new SequentialAction(
                                outtake.prepareToTransfer(),
                                intake.prepareStackIntake(),
                                intake.intakeOn()));
            }
        }

        if(g1.guideOnce()) {
            sched.queueAction(outtake.reverseDump());
        }
    }
    private void field_centric_move() {
        double speed = (1-Math.abs(g1.right_stick_x)) * (DRIVE_SPEED - SLOW_DRIVE_SPEED) + SLOW_DRIVE_SPEED;
        double input_x = Math.pow(-g1.left_stick_y, 3) * speed;
        double input_y = Math.pow(-g1.left_stick_x, 3) * speed;
        Vector2d input = new Vector2d(input_x, input_y);
        input = drive.pose.heading.times(input);

        double input_turn = Math.pow(g1.left_trigger - g1.right_trigger, 3) * TURN_SPEED;
        if (g1.leftBumper()) input_turn += SLOW_TURN_SPEED;
        if (g1.rightBumper()) input_turn -= SLOW_TURN_SPEED;

        drive.setDrivePowers(new PoseVelocity2d(input, input_turn));
    }

    final public void update() {
        this.drive.updatePoseEstimate();
        telemetry.update();
    }
}


package org.firstinspires.ftc.teamcode.subsystem;

import android.util.Log;

import com.acmerobotics.dashboard.config.Config;
import com.acmerobotics.dashboard.telemetry.TelemetryPacket;
import com.acmerobotics.roadrunner.Action;
import com.acmerobotics.roadrunner.SequentialAction;
import com.acmerobotics.roadrunner.SleepAction;
import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.DigitalChannel;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;

import org.firstinspires.ftc.teamcode.utils.software.ActionUtil;
import org.firstinspires.ftc.teamcode.utils.hardware.HardwareCreator;

@Config
public class Intake {
    public static int INTAKE_SPEED = 950;
    final DcMotorEx intakeMotor;
    final Servo stackIntakeLinkage;
    final Servo stackIntakeServoLeft;
    final Servo stackIntakeServoRight;
    final CRServo bottomRollerServo;

    public static double STACK_INTAKE_LEFT_INIT = 0.01;
    public static double STACK_INTAKE_LEFT_PRELOAD = 0.15;
    public static double STACK_INTAKE_LEFT_1ST_PIXEL = 0.58;
    public static double STACK_INTAKE_LEFT_2nd_PIXEL = 1.0;

    public static double STACK_INTAKE_RIGHT_INIT = 0.02;
    public static double STACK_INTAKE_RIGHT_PRELOAD = 0.11;
    public static double STACK_INTAKE_RIGHT_1ST_PIXEL = 0.58;
    public static double STACK_INTAKE_RIGHT_2nd_PIXEL = 1.0;

    public static double STACK_INTAKE_LINKAGE_INIT = 0.95;
    public static double STACK_INTAKE_LINKAGE_DOWN = 0.489;
    public static double STACK_INTAKE_LINKAGE_UP = 0.89;

    public static double AUTO_INTAKE_REVERSE_TIME = 2500;
    private boolean isAutoReverseOn = true;

    private final DigitalChannel beamBreakerActive;
    private final DigitalChannel beamBreakerPassive;

    private boolean prevBeamBreakerState = true;
    private boolean curBeamBreakerState = true;

    public static int totalPixelCount = 0;
    public static int pixelsCount = 0;

    private Long intakeReverseTime = null;

    private long lastPixelDetectedTime = 0;

    public Intake(HardwareMap hardwareMap) {
        this.intakeMotor = HardwareCreator.createMotor(hardwareMap, "intake_for_perp");
        this.intakeMotor.setDirection(DcMotorSimple.Direction.REVERSE);

        stackIntakeServoLeft = HardwareCreator.createServo(hardwareMap, "stackIntakeServoLeft");
        stackIntakeServoRight = HardwareCreator.createServo(hardwareMap, "stackIntakeServoRight");
        stackIntakeLinkage = HardwareCreator.createServo(hardwareMap, "stackIntakeLinkage");
        bottomRollerServo = HardwareCreator.createCRServo(hardwareMap, "bottomRollerServo", HardwareCreator.ServoType.AXON);

        beamBreakerActive = hardwareMap.get(DigitalChannel.class, "beamBreaker1");
        beamBreakerPassive = hardwareMap.get(DigitalChannel.class, "beamBreaker2");
    }

    public void initialize(boolean isAuto) {
        if( isAuto) {
            stackIntakeLinkage.setPosition(STACK_INTAKE_LINKAGE_INIT);
            stackIntakeServoLeft.setPosition(STACK_INTAKE_LEFT_PRELOAD);
            stackIntakeServoRight.setPosition(STACK_INTAKE_RIGHT_PRELOAD);
        }
        else {
            stackIntakeLinkage.setPosition(STACK_INTAKE_LINKAGE_UP);
            stackIntakeServoLeft.setPosition(STACK_INTAKE_LEFT_INIT);
            stackIntakeServoRight.setPosition(STACK_INTAKE_RIGHT_INIT);
        }

        intakeState = IntakeState.OFF;
        stackIntakeState = StackIntakeState.UP;
        totalPixelCount = 0;
        pixelsCount = 0;
    }

    public enum IntakeState {
        OFF,
        REVERSING,
        ON
    }

    public enum StackIntakeState {
        INIT,
        UP,
        DOWN
    }
    public IntakeState intakeState;

    public StackIntakeState stackIntakeState;

    public class IntakeStateAction implements Action {
        IntakeState newState;

        public IntakeStateAction(IntakeState newState) {
            this.newState = newState;
        }

        @Override
        public boolean run(TelemetryPacket packet) {
            intakeState = newState;
            return false;
        }
    }

    public class StackIntakeStateAction implements Action {
        StackIntakeState newState;

        public StackIntakeStateAction(StackIntakeState newState) {
            this.newState = newState;
        }

        @Override
        public boolean run(TelemetryPacket packet) {
            stackIntakeState = newState;
            return false;
        }
    }

    public static class UpdatePixelCountAction implements Action {
        int changeValue;

        public UpdatePixelCountAction(int value) {
            this.changeValue = value;
        }

        @Override
        public boolean run(TelemetryPacket packet) {
            int prevPixel = pixelsCount;
            pixelsCount = pixelsCount + changeValue;
            if(pixelsCount < 0) pixelsCount =0;
            if(prevPixel != pixelsCount) {
                Log.d("Intake_Pixel_Detection", "New pixel count change from " + prevPixel + " to " + pixelsCount + " | total pixels taken: " + totalPixelCount);
            }
            return false;
        }
    }

    public Action intakeOn() {
        Log.d("Intake_Motor","Intake is on");
        return new SequentialAction(
                new IntakeStateAction(IntakeState.ON),
                new ActionUtil.CRServoAction(bottomRollerServo, 1.0),
                new ActionUtil.DcMotorExPowerAction(intakeMotor, INTAKE_SPEED / 1000.0)
        );
    }

    public Action intakeReverse() {
        Log.d("Intake_Motor","Intake is reversing");
        return new SequentialAction(
                new IntakeStateAction(IntakeState.REVERSING),
                new ActionUtil.CRServoAction(bottomRollerServo, -1.0),
                new ActionUtil.ServoPositionAction(stackIntakeLinkage, STACK_INTAKE_LINKAGE_UP, "stackIntakeLinkage"),
                new ActionUtil.DcMotorExPowerAction(intakeMotor, -INTAKE_SPEED / 1000.0)
        );
    }

    public Action intakeOff() {
        Log.d("Intake_Motor","Intake is off");
        return new SequentialAction(
                new IntakeStateAction(IntakeState.OFF),
                new ActionUtil.DcMotorExPowerAction(intakeMotor, 0.0),
                new ActionUtil.CRServoAction(bottomRollerServo, 0.0)
        );
    }

    public Action intakeSlowdown() {
        Log.d("Intake_Motor","Intake is slowing down");
        return new SequentialAction(
                new IntakeStateAction(IntakeState.ON),
                new ActionUtil.CRServoAction(bottomRollerServo, -1.0),
                new ActionUtil.DcMotorExPowerAction(intakeMotor, -(INTAKE_SPEED / 1000.0)*0.25)
        );
    }

    public Action prepareStackIntake() {
        return new SequentialAction(
                new StackIntakeStateAction(StackIntakeState.DOWN),
                new ActionUtil.ServoPositionAction(stackIntakeServoLeft, STACK_INTAKE_LEFT_INIT, "stackIntakeServoLeft"),
                new ActionUtil.ServoPositionAction(stackIntakeServoRight, STACK_INTAKE_RIGHT_INIT, "stackIntakeServoRight"),
                new ActionUtil.ServoPositionAction(stackIntakeLinkage, STACK_INTAKE_LINKAGE_DOWN, "stackIntakeLinkage"),
                intakeOn()
        );
    }

    public Action prepareTeleOpsIntake() {
        return new SequentialAction(
                new StackIntakeStateAction(StackIntakeState.UP),
                new ActionUtil.ServoPositionAction(stackIntakeLinkage, STACK_INTAKE_LINKAGE_UP, "stackIntakeLinkage"),
                new SleepAction(0.2),
                new ActionUtil.ServoPositionAction(stackIntakeServoLeft, STACK_INTAKE_LEFT_INIT, "stackIntakeServoLeft"),
                new ActionUtil.ServoPositionAction(stackIntakeServoRight, STACK_INTAKE_RIGHT_INIT, "stackIntakeServoRight"),
                intakeOff()
        );
    }

    public Action intakeStackedPixels() {
        return new SequentialAction(
                new ActionUtil.ServoPositionAction(stackIntakeServoRight, STACK_INTAKE_RIGHT_1ST_PIXEL, "stackIntakeServoRight"),
                new SleepAction(0.20),
                new ActionUtil.ServoPositionAction(stackIntakeServoLeft, STACK_INTAKE_LEFT_1ST_PIXEL, "ServoPositionAction"),
                new SleepAction(0.75),
                new ActionUtil.ServoPositionAction(stackIntakeServoRight, STACK_INTAKE_RIGHT_2nd_PIXEL, "stackIntakeServoRight"),
                new SleepAction(0.20),
                new ActionUtil.ServoPositionAction(stackIntakeServoLeft, STACK_INTAKE_LEFT_2nd_PIXEL, "stackIntakeServoLeft"),
                new SleepAction(0.5)
        );
    }

    public Action scorePurplePreload() {
        return new SequentialAction(
                new ActionUtil.ServoPositionAction(stackIntakeServoLeft, STACK_INTAKE_LEFT_INIT, "stackIntakeServoLeft"),
                new ActionUtil.ServoPositionAction(stackIntakeServoRight, STACK_INTAKE_RIGHT_INIT, "stackIntakeServoRight")
        );
    }

    public Action stackIntakeLinkageDown() {
        return new SequentialAction(
                new ActionUtil.ServoPositionAction(stackIntakeLinkage, STACK_INTAKE_LINKAGE_DOWN, "stackIntakeLinkage"),
                new StackIntakeStateAction(StackIntakeState.DOWN)
        );
    }
    public Action stackIntakeLinkageUp() {
        return new SequentialAction(
                new ActionUtil.ServoPositionAction(stackIntakeLinkage, STACK_INTAKE_LINKAGE_UP, "stackIntakeLinkage"),
                new StackIntakeStateAction(StackIntakeState.UP)
        );
    }

    public void turnOffAutoReverse() {
        this.isAutoReverseOn = false;
    }

    public void update() {

        curBeamBreakerState = beamBreakerActive.getState();

        if(prevBeamBreakerState != curBeamBreakerState) {
            Log.d("Beam_Breaker_Pixel_Changed", "Prev_BeamBreaker State: " + prevBeamBreakerState + " | current_BeamBreaker state: " + curBeamBreakerState);
        }

        // update pixel detection state if beam
        if(!curBeamBreakerState && prevBeamBreakerState && intakeState == IntakeState.ON) {
            pixelsCount++;
            totalPixelCount++;
            Log.d("Beam_Breaker_Pixel_Detected", "Detected!!!! | totalPixels: " + totalPixelCount + " | currentPixels: " + pixelsCount);
            Log.d("Beam_Breaker_Pixel_Timer", "Detected at: " + System.currentTimeMillis());
        }

        prevBeamBreakerState = curBeamBreakerState;
    }
    public String getStackServoPositions() {
        return String.format("Left stack servo: %.3f | Right stack servo: %.3f ",
                this.stackIntakeServoLeft.getPosition(), this.stackIntakeServoRight.getPosition());
    }
}

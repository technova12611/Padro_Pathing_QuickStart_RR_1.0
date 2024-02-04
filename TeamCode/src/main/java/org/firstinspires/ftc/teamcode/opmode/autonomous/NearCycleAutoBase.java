package org.firstinspires.ftc.teamcode.opmode.autonomous;

import android.util.Log;

import com.acmerobotics.roadrunner.Action;
import com.acmerobotics.roadrunner.ParallelAction;
import com.acmerobotics.roadrunner.Pose2d;
import com.acmerobotics.roadrunner.SequentialAction;
import com.acmerobotics.roadrunner.SleepAction;
import com.acmerobotics.roadrunner.Vector2d;

import org.firstinspires.ftc.teamcode.Globals;
import org.firstinspires.ftc.teamcode.MecanumDrive;
import org.firstinspires.ftc.teamcode.pipeline.AlliancePosition;
import org.firstinspires.ftc.teamcode.pipeline.FieldPosition;
import org.firstinspires.ftc.teamcode.roadrunner.messages.PoseMessage;
import org.firstinspires.ftc.teamcode.subsystem.Outtake;
import org.firstinspires.ftc.teamcode.utils.software.ActionUtil;

public abstract class NearCycleAutoBase extends AutoBase {
    // 0 = left, 1 = middle, 2 = right
    public Pose2d start;
    public Pose2d start_forward;
    public Pose2d[] backdrop ;
    public Pose2d[] spike;
    public Pose2d[] cycleStart;
    public Pose2d stackAlignment;
    public Pose2d stackIntake1;
    public Pose2d stackIntake2;
    public Pose2d safeTrussPassStop;
    public Pose2d backdropAlignment;
    public Pose2d[] cycleScore;
    public Pose2d parking;
    private int cycleCount = 0;

    boolean relocalization = true;

    protected void onInit() {
        // this is the default
        super.onInit();
        this.stackPosition = stackIntake1;
        this.sched.setStackAlignmentCallback(this);
        this.sched.setBackdropAlignmentCallback(this);
    }

    @Override
    protected void onRun() {

        Log.d("Drive_logger", "starting:" + new PoseMessage(drive.pose));
        Log.d("Drive_logger", "backdrop position:" + new PoseMessage(backdrop[SPIKE]));

        sched.addAction(
                new SequentialAction(
                        new MecanumDrive.DrivePoseLoggingAction(drive, "starting_position"),
                        outtake.prepareToSlide(),
                        // to score yellow pixel on the backdrop
                        new ParallelAction(
                                drive.actionBuilder(drive.pose)
                                        .strafeTo(start_forward.position)
                                        .strafeToLinearHeading(backdrop[SPIKE].position, backdrop[SPIKE].heading)
                                        .build(),

                                new SequentialAction(
                                        new SleepAction(1.0),
                                        outtake.extendOuttakeLow()
                                )
                        ),

                        new MecanumDrive.DrivePoseLoggingAction(drive, "backdrop_position"),
                        //new MecanumDrive.UpdateDrivePoseAction(drive, this.visionPortal2, this.aprilTag),

                        outtake.prepareToScore(),
                        new SleepAction(0.30),
                        getBackdropDistanceAdjustmentAction(),
                        outtake.latchScore1(),
                        new SleepAction(0.50),
                        intake.stackIntakeLinkageDown(),
                        outtake.afterScore(),
                        new SleepAction(0.3),
                        new ActionUtil.RunnableAction(() -> {
                            pidDriveActivated = false;
                            straightDistance = 0.0;
                            return false;
                        }),
                        new MecanumDrive.DrivePoseLoggingAction(drive, "end_of_scoring_position"),
                        new ParallelAction(
                                outtake.retractOuttake(),

                                // to score the purple pixel on the spike
                                drive.actionBuilder(backdrop[SPIKE])
                                        .strafeToLinearHeading(spike[SPIKE].position, spike[SPIKE].heading, this.drive.highSpeedVelConstraint, this.drive.highSpeedAccelConstraint)
                                        .build()
                        ),

                        new MecanumDrive.DrivePoseLoggingAction(drive, "spike_position"),

                        intake.scorePurplePreload(),
                        new SleepAction(0.2)
                )
        );

//        sched.addAction(
//                new SequentialAction(
//                new ParallelAction(
//                        outtake.retractOuttake(),
//                        intake.prepareTeleOpsIntake(),
//                        drive.actionBuilder(spike[SPIKE]) //spike[SPIKE]
//                                .strafeToLinearHeading(cycleStart[SPIKE].position, cycleStart[SPIKE].heading, this.drive.highSpeedVelConstraint, this.drive.highSpeedAccelConstraint)
//                                .build()
//                        ),
//
//                        new MecanumDrive.DrivePoseLoggingAction(drive, "start_cycle_position", true)
//                )
//        );

        // do the first cycle from the spike position
        cyclePixelFromStack(spike[SPIKE]);

        // do the 2nd cycle from the cycle drop position
        cyclePixelFromStack(cycleScore[SPIKE]);

        sched.addAction(new ParallelAction(
                        new SequentialAction(
                                new MecanumDrive.DrivePoseLoggingAction(drive, "start_of_retracting"),
                                outtake.fastRetractOuttake(0.3),
                                new SleepAction(0.5),
                                intake.prepareTeleOpsIntake(),
                                outtake.prepareToTransfer(),
                                new MecanumDrive.DrivePoseLoggingAction(drive, "slides_retracted_completed")
                        ),

                        new SequentialAction(
                                new MecanumDrive.DrivePoseLoggingAction(drive, "start_of_parking"),
                                // to score the purple pixel on the spike
                                drive.actionBuilder(cycleScore[SPIKE])
                                        .strafeTo(parking.position)
                                        .build(),
                                new MecanumDrive.DrivePoseLoggingAction(drive, "end_of_parking")
                        )
                )
        );
    }

    private void cyclePixelFromStack(Pose2d startingPosition) {
        Action extendSlideAction;
        Pose2d stackIntakePosition;
        Vector2d cycleScorePosition = cycleScore[SPIKE].position;
        Pose2d cycleStartPose = this.cycleStart[SPIKE];

        if(++cycleCount == 2) {
            extendSlideAction = outtake.extendOuttakeCycleTwo();
            stackIntakePosition = stackIntake2;
            // do we need to move back, need to test more, changed from 0.5 -> 0.25 for now
            cycleScorePosition = new Vector2d(cycleScorePosition.x, cycleScorePosition.y);
            if(Globals.COLOR == AlliancePosition.RED && SPIKE == 0) {
                cycleStartPose = this.cycleStart[1];
            } else if (Globals.COLOR == AlliancePosition.BLUE && SPIKE == 2) {
                cycleStartPose = this.cycleStart[1];
            }

            relocalization = false;
        } else {
            extendSlideAction = outtake.extendOuttakeCycleOne();
            stackIntakePosition = stackIntake1;
        }

        sched.addAction(
                new SequentialAction(
                        // to strafe to cycle start teleops
                        new ParallelAction(
                                outtake.fastRetractOuttake(0.5),
                                intake.prepareTeleOpsIntake(),
                                drive.actionBuilder(startingPosition) //spike[SPIKE]
                                        .strafeToLinearHeading(cycleStartPose.position, cycleStartPose.heading,
                                                this.drive.highSpeedVelConstraint,
                                                this.drive.highSpeedAccelConstraint)
                                        .strafeToLinearHeading(stackAlignment.position,stackAlignment.heading,
                                                this.drive.highSpeedVelConstraint, this.drive.highSpeedAccelConstraint)
                                        .build(),
                                new SequentialAction(
                                        new SleepAction(1.8),
                                        outtake.prepareToTransfer(),
                                        new SleepAction(0.5),
                                        intake.stackIntakeLinkageDown()
                                )
                        ),

                        new MecanumDrive.DrivePoseLoggingAction(drive, "stack_alignment_position")
                ));

        sched.addAction(
                new AutoBase.StackIntakePositionAction(drive,intake,stackIntakePosition)
        );

        sched.addAction(
                new SequentialAction(
                        // move back to the backdrop
                        new ParallelAction(
                                new SequentialAction(
                                    drive.actionBuilder(safeTrussPassStop)
                                            .setReversed(true)
                                            .strafeToLinearHeading(backdropAlignment.position,backdropAlignment.heading,
                                                    this.drive.highSpeedVelConstraint,
                                                    this.drive.highSpeedAccelConstraint)
                                            .build(),
                                         new MecanumDrive.DrivePoseLoggingAction(drive, "backdrop_alignment_end")
                                ),

                                new SequentialAction(
                                        new SleepAction(2.1),
                                        intake.prepareTeleOpsIntake(),
                                        new MecanumDrive.DrivePoseLoggingAction(drive, "Intake_off")
                                )
                        ),

                        new MecanumDrive.DrivePoseLoggingAction(drive, "Before_backdrop_score"),
                        new MecanumDrive.AutoPositionCheckAction(drive, backdropAlignment),

                        new BackdropRelocalizationAction(drive, outtake, cycleScore[SPIKE]),
                        new MecanumDrive.DrivePoseLoggingAction(drive, "after_localization"),

                        // move to backdrop scoring position
                        new ParallelAction(
                                new SequentialAction(
                                    drive.actionBuilder(backdropAlignment)
                                            .setReversed(true)
                                            .strafeToLinearHeading(cycleScorePosition, cycleScore[SPIKE].heading,
                                                    drive.slowVelConstraint, drive.slowAccelConstraint)
                                            .build(),
                                        new MecanumDrive.DrivePoseLoggingAction(drive, "cycle_" + cycleCount + "_score_position")
                                ),

                                new SequentialAction(
                                        outtake.prepareToSlide(),
                                        new SleepAction(0.3),
                                        new MecanumDrive.DrivePoseLoggingAction(drive, "cycle_" + cycleCount + "_prepare"),
                                        extendSlideAction,
                                        new MecanumDrive.DrivePoseLoggingAction(drive, "cycle_" + cycleCount + "_extend"),
                                        new SleepAction(0.3),
                                        outtake.prepareToScoreCycle(),
                                        new SleepAction(0.2)
                                )
                        )
                ));

//        sched.addAction(new BackdropAlignmentAction(drive, outtake, backdrop[SPIKE]));

        sched.addAction(
                new SequentialAction(
                    // score pixels
                    new MecanumDrive.DrivePoseLoggingAction(drive, "cycle_score_" + cycleCount + "_open_latch_start"),

                        getBackdropDistanceAdjustmentAction(),

                        outtake.latchScore1(),
                    new SleepAction(0.25),
                    outtake.latchScore2(),
                    new SleepAction(0.45),
                    new ActionUtil.RunnableAction(() -> {
                        pidDriveActivated = false;
                        pidDriveStarted = false;
                        pidDriveStraight.resetIntegralGain();
                        straightDistance = 0.0;
                        return false;
                    })
                )
        );

        sched.addAction(
                new MecanumDrive.DrivePoseLoggingAction(drive, "cycle_" + cycleCount + "_score_end")
        );
    }

    @Override
    public FieldPosition getFieldPosition() {
        return FieldPosition.NEAR;
    }

    @Override
    protected Pose2d getStartPose() {
        return this.start;
    }

    @Override
    public Action driveToStack() {
        double offset = (relocalization)?0.5:0.0;
        Vector2d intakeBackOffPose = new Vector2d(getStackPosition().position.x+0.5, getStackPosition().position.y);
        Vector2d trussAlignmentPose = new Vector2d(safeTrussPassStop.position.x, getStackPosition().position.y);

        return
            new SequentialAction(
                new ParallelAction(
                    new MecanumDrive.DrivePoseLoggingAction(drive, "cycle_" + cycleCount + "_auto_alignment_start"),
                    drive.actionBuilder(stackAlignment)
                         .strafeToLinearHeading(getStackPosition().position, getStackPosition().heading,
                                 drive.slowVelConstraint, drive.slowAccelConstraint)
                         .build(),
                    intake.intakeOn()
                ),

               new ParallelAction(
                       new SequentialAction(
                               new MecanumDrive.DrivePoseLoggingAction(drive, "stack_intake_start", true),
                           intake.intakeTwoStackedPixels2(),
                               new MecanumDrive.DrivePoseLoggingAction(drive, "stack_intake_end", true),
                               new ActionUtil.RunnableAction(() -> {

                                   if(relocalization) {
                                       drive.pose = new Pose2d(drive.pose.position.plus(new Vector2d(AutoBase.x_adjustment, AutoBase.y_adjustment)), drive.pose.heading);
                                       drive.updatePoseEstimate();
                                   }
                                   return false;
                               }),
                           new MecanumDrive.DrivePoseLoggingAction(drive, "stack_intake_after_adjustment", true)
                       )

//                       new SequentialAction(
//                               new MecanumDrive.DrivePoseLoggingAction(drive, "stack_intake_pose_adj", true),
//                               new SleepAction(0.5),
//                               new MecanumDrive.DrivePoseLoggingAction(drive, "stack_intake_pose_adj", true),
//                               drive.actionBuilder(getStackPosition())
//                                    .setReversed(true)
//                                    .strafeToLinearHeading(intakeBackOffPose,getStackPosition().heading,
//                                            drive.slowVelConstraint, drive.slowAccelConstraint)
//                                   .build(),
//                               new MecanumDrive.DrivePoseLoggingAction(drive, "stack_intake_backoff", true)
//                       )
               ),
               // move back to the backdrop

                drive.actionBuilder(new Pose2d(intakeBackOffPose, getStackPosition().heading))
                        .setReversed(true)
                        .strafeToLinearHeading(trussAlignmentPose,safeTrussPassStop.heading)
//                                this.drive.highSpeedVelConstraint,
//                                this.drive.highSpeedAccelConstraint)
                        .build(),
                intake.stackIntakeLinkageUp(),
                new MecanumDrive.DrivePoseLoggingAction(drive, "backdrop_alignment_end")
            );
    }
}

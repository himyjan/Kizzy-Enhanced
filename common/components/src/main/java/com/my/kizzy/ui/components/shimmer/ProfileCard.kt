/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * ProfileCard.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.ui.components.shimmer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Skeleton of the Discord-style profile page.
 *
 * The placeholder mirrors the real [com.my.kizzy.feature_profile.ui.component.ProfileCard]
 * structure - full-bleed banner, avatar hanging over its bottom edge, name /
 * username rows, badge pill, bio lines and the activity card - so the screen
 * does not visibly jump from the old framed layout to the new one when the
 * data arrives.
 */
@Composable
fun ShimmerProfileCard(
    brush: Brush
) {
    Column(Modifier.fillMaxWidth()) {
        // Banner + overlapping avatar (same silhouette as ProfileCard).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(188.dp),
        ) {
            Spacer(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(brush),
            )
            Spacer(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp)
                    .size(96.dp)
                    .border(5.dp, brush, CircleShape)
                    .clip(CircleShape)
                    .background(brush),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(10.dp))

            // Display name.
            Spacer(
                modifier = Modifier
                    .height(26.dp)
                    .fillMaxWidth(0.35f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(brush),
            )

            // Username + custom status chip.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Spacer(
                    modifier = Modifier
                        .height(18.dp)
                        .fillMaxWidth(0.45f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush),
                )
                Spacer(Modifier.width(8.dp))
                Spacer(
                    modifier = Modifier
                        .height(24.dp)
                        .width(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brush),
                )
            }

            // Badge pill.
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(brush)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                repeat(3) {
                    Spacer(
                        modifier = Modifier
                            .size(30.dp)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(brush),
                    )
                }
            }

            // Bio: heading + two lines.
            Spacer(Modifier.height(16.dp))
            Spacer(
                modifier = Modifier
                    .height(14.dp)
                    .fillMaxWidth(0.2f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush),
            )
            Spacer(Modifier.height(8.dp))
            repeat(2) {
                Spacer(
                    modifier = Modifier
                        .height(16.dp)
                        .fillMaxWidth(if (it == 0) 0.9f else 0.6f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush),
                )
                Spacer(Modifier.height(6.dp))
            }

            // Activity card.
            Spacer(Modifier.height(18.dp))
            ShimmerRpcRow(brush)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun ShimmerRpcRow(
    brush: Brush
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(brush)
            .padding(14.dp),
    ) {
        // Card header ("Listening to ...").
        Spacer(
            modifier = Modifier
                .height(18.dp)
                .fillMaxWidth(0.5f)
                .clip(RoundedCornerShape(6.dp))
                .background(brush),
        )

        // Artwork + title/state.
        Row(
            modifier = Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Spacer(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush),
            )
            Column(Modifier.padding(start = 12.dp)) {
                Spacer(
                    modifier = Modifier
                        .height(18.dp)
                        .fillMaxWidth(0.8f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush),
                )
                Spacer(Modifier.height(8.dp))
                Spacer(
                    modifier = Modifier
                        .height(15.dp)
                        .fillMaxWidth(0.5f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush),
                )
            }
        }

        // Timeline row.
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                modifier = Modifier
                    .height(13.dp)
                    .width(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush),
            )
            Spacer(Modifier.width(8.dp))
            Spacer(
                modifier = Modifier
                    .height(4.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(brush),
            )
            Spacer(Modifier.width(8.dp))
            Spacer(
                modifier = Modifier
                    .height(13.dp)
                    .width(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush),
            )
        }

        // Action buttons.
        Spacer(Modifier.height(12.dp))
        repeat(2) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(brush),
            )
            if (it == 0) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
@Preview(showBackground = true)
fun PreviewShimmerProfileCard() {
    AnimatedShimmer {
        ShimmerProfileCard(brush = it)
    }
}

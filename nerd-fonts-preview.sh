#!/bin/zsh

# Battletech TUI - Terrain Icon Preview Script
# Updated for ZSH with native Unicode support and high-plane glyphs.
# REQUIRES: A terminal with a Nerd Font installed and TrueColor support.

# ANSI Color Codes (TrueColor / 24-bit)
RESET="\033[0m"
BOLD="\033[1m"

GREEN_LIGHT="64;255;64"
GREEN="16;185;129"
CYAN="34;211;238"
BLUE="59;130;246"
ORANGE="253;186;116"
GRAY="156;163;175"
INDIGO="165;180;252"
SLATE="203;213;225"
RED="239;68;68"

GREEN_LIGHT_BG="10;40;10"
GREEN_BG="5;40;20"
CYAN_BG="10;45;60"
BLUE_BG="10;30;80"
ORANGE_BG="50;30;10"
GRAY_BG="40;40;40"
INDIGO_BG="20;20;50"
SLATE_BG="50;50;50"
RED_BG="60;10;10"

# Terrain Definitions [Name;HexCode;FG_R;FG_G;FG_B;BG_R;BG_G;BG_B]
# Using \U escape for characters above FFFF (8-digit hex required)
TERRAINS=(
  "Light Woods;nf-md-tree_outline;f0e69;${GREEN_LIGHT};${GREEN_LIGHT_BG}"
  "Heavy Woods;nf-md-tree;f0531;${GREEN};${GREEN_BG}"
  "Shallow Water;nf-md-wave;f0f2e;${CYAN};${CYAN_BG}"
  "Deep Water;nf-fa-droplet;f043;${BLUE};${BLUE_BG}"
  "Deep Water;nf-md-waves;f078d;${BLUE};${BLUE_BG}"
  "Deep Water;nf-fa-water;ef30;${BLUE};${BLUE_BG}"
  "Rough;nf-fae-mountains;e2a6;${ORANGE};${ORANGE_BG}"
  "Rubble;nf-fa-cubes;f1b3;${GRAY};${GRAY_BG}"
  "Rubble;nf-fa-cube;f1b2;${GRAY};${GRAY_BG}"
  "Rubble;nf-md-cube_outline;f01a7;${GRAY};${GRAY_BG}"
  "Building;nf-fa-building;f1ad;${INDIGO};${INDIGO_BG}"
  "Building;nf-md-office_building;f0991;${INDIGO};${INDIGO_BG}"
  "Pavement;nf-fa-road;f018;${SLATE};${SLATE_BG}"
  "Pavement;nf-md-road;f0461;${SLATE};${SLATE_BG}"
  "Pavement;nf-md-road_variant;f0462;${SLATE};${SLATE_BG}"
  "Fire;nf-fa-fire;f06d;${RED};${RED_BG}"
  "Fire;nf-md-fire;f0238;${RED};${RED_BG}"
  "Tree Fire;nf-md-pine_tree_fire;f141a;${RED};${RED_BG}"
  "Direction N;nf-md-arrow_up;f005d;${SLATE};${SLATE_BG}"
  "Direction NE;nf-md-arrow_top_right;f005c;${SLATE};${SLATE_BG}"
  "Direction E;nf-md-arrow_right;f0054;${SLATE};${SLATE_BG}"
  "Direction SE;nf-md-arrow_bottom_right;f0043;${SLATE};${SLATE_BG}"
  "Direction S;nf-md-arrow_down;f0045;${SLATE};${SLATE_BG}"
  "Direction SW;nf-md-arrow_bottom_left;f0042;${SLATE};${SLATE_BG}"
  "Direction W;nf-md-arrow_left;f004d;${SLATE};${SLATE_BG}"
  "Direction NW;nf-md-arrow_top_left;f005b;${SLATE};${SLATE_BG}"
  "Direction N;nf-md-arrow_up_bold_outline;f09c7;${SLATE};${SLATE_BG}"
  "Direction NE;nf-md-arrow_top_right_bold_outline;f09c5;${SLATE};${SLATE_BG}"
  "Direction E;nf-md-arrow_right_bold_outline;f09c2;${SLATE};${SLATE_BG}"
  "Direction SE;nf-md-arrow_bottom_right_bold_outline;f09b9;${SLATE};${SLATE_BG}"
  "Direction S;nf-md-arrow_down_bold_outline;f09bf;${SLATE};${SLATE_BG}"
  "Direction SW;nf-md-arrow_bottom_left_bold_outline;f09b7;${SLATE};${SLATE_BG}"
  "Direction W;nf-md-arrow_left_bold_outline;f09c0;${SLATE};${SLATE_BG}"
  "Direction NW;nf-md-arrow_top_left_bold_outline;f09c3;${SLATE};${SLATE_BG}"
  "Walk;nf-md-walk;f0583;${SLATE};${SLATE_BG}"
  "Run;nf-md-run_fast;f046e;${SLATE};${SLATE_BG}"
  "Jump;nf-md-rocket_launch;f14de;${SLATE};${SLATE_BG}"
  "Level 1;nf-md-numeric_1_box_multiple_outline;f03a5;${SLATE};${SLATE_BG}"
  "Level 2;nf-md-numeric_2_box_multiple_outline;f03a8;${SLATE};${SLATE_BG}"
  "Level 3;nf-md-numeric_3_box_multiple_outline;f03ab;${SLATE};${SLATE_BG}"
  "Level 4;nf-md-numeric_4_box_multiple_outline;f03b2;${SLATE};${SLATE_BG}"
  "Level 5;nf-md-numeric_5_box_multiple_outline;f03af;${SLATE};${SLATE_BG}"
  "Level 6;nf-md-numeric_6_box_multiple_outline;f03b4;${SLATE};${SLATE_BG}"
  "Level 7;nf-md-numeric_7_box_multiple_outline;f03b7;${SLATE};${SLATE_BG}"
  "Level 8;nf-md-numeric_8_box_multiple_outline;f03ba;${SLATE};${SLATE_BG}"
  "Level 9;nf-md-numeric_9_box_multiple_outline;f03bd;${SLATE};${SLATE_BG}"
  "Dice 1;nf-fa-dice_one;edef;${SLATE};${SLATE_BG}"
  "Dice 1;nf-md-dice_1;f01ca;${SLATE};${SLATE_BG}"
  "Check;nf-fa-check;f00c;${GREEN_LIGHT};${GREEN_LIGHT_BG}"
  "Check;nf-md-check;f012c;${GREEN_LIGHT};${GREEN_LIGHT_BG}"
  "Check;nf-oct-check;f42e;${GREEN_LIGHT};${GREEN_LIGHT_BG}"
  "Check;nf-md-check_bold;f0e1e;${GREEN_LIGHT};${GREEN_LIGHT_BG}"
  "Checkbox;nf-md-checkbox_blank_outline;f0131;${GRAY};${GRAY_BG}"
  "Checkbox;nf-md-checkbox_marked_outline;f0135;${GREEN_LIGHT};${GREEN_LIGHT_BG}"
  "Checkbox;nf-md-minus_box_outline;f06f2;${GRAY};${GRAY_BG}"
  "Ammunition;nf-md-ammunition;f0ce8;${SLATE};${SLATE_BG}"
  "Infinity;nf-fa-infinity;edfe;${SLATE};${SLATE_BG}"
  "Infinity;nf-md-infinity;f06e4;${SLATE};${SLATE_BG}"
  "Circle;nf-md-checkbox_blank_circle_outline;f0130;${SLATE};${SLATE_BG}"
  "Circle;nf-md-circle_half_full;f1396;${SLATE};${SLATE_BG}"
  "Circle;nf-md-circle_medium;f09de;${SLATE};${SLATE_BG}"
  "Circle;nf-md-circle_small;f09df;${SLATE};${SLATE_BG}"
  "Circle;nf-md-circle_slice_2;f0a9f;${SLATE};${SLATE_BG}"
  "Circle;nf-md-circle_slice_4;f0aa1;${SLATE};${SLATE_BG}"
  "Circle;nf-md-circle_slice_6;f0aa3;${SLATE};${SLATE_BG}"
  "Circle;nf-md-circle_slice_8;f0aa5;${SLATE};${SLATE_BG}"

)

echo -e "${BOLD}Battletech TUI: Terrain Glyph Preview (ZSH Native)${RESET}"
echo "------------------------------------------------------------------------------------------"
printf "%-15s | %-37s | %-7s | %-5s | %-10s\n" "Terrain" "Nerd Font" "Hex" "Icon" "CLI Test"
echo "------------------------------------------------------------------------------------------"

for terrain in "${TERRAINS[@]}"; do
  IFS=";" read -r name nerd_class hex fg_r fg_g fg_b bg_r bg_g bg_b <<<"$terrain"

  glyph=$(printf "\U$hex")

  FG="\033[38;2;${fg_r};${fg_g};${fg_b}m"
  BG="\033[48;2;${bg_r};${bg_g};${bg_b}m"

  # Print the result
  printf "%-15s | %-37s | %-7s |  ${FG}%s${RESET}    | ${BG}${FG} %s ${RESET}\n" "$name" "$nerd_class" "$hex" "$glyph" "$glyph"
done

echo "------------------------------------------------------------------------------------------"
echo -e "${BOLD}Note:${RESET} Running in ZSH. Light Woods uses high-plane hex: f0e69."
echo "The script automatically toggles between \u and \U for glyph expansion."

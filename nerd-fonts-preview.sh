#!/bin/zsh

# Battletech TUI - Terrain Icon Preview Script
# Updated for ZSH with native Unicode support and high-plane glyphs.
# REQUIRES: A terminal with a Nerd Font installed and TrueColor support.

# ANSI Color Codes (TrueColor / 24-bit)
RESET="\033[0m"
BOLD="\033[1m"

# Terrain Definitions [Name;HexCode;FG_R;FG_G;FG_B;BG_R;BG_G;BG_B]
# Using \U escape for characters above FFFF (8-digit hex required)
TERRAINS=(
  "Light Woods;nf-md-tree_outline;f0e69;64;255;64;10;40;10"
  "Heavy Woods;nf-md-tree;f0531;16;185;129;5;40;20"
  "Shallow Water;nf-md-wave;f0f2e;34;211;238;10;45;60"
  "Deep Water;nf-fa-droplet;f043;59;130;246;10;30;80"
  "Deep Water;nf-md-waves;f078d;59;130;246;10;30;80"
  "Deep Water;nf-fa-water;ef30;59;130;246;10;30;80"
  "Rough;nf-fae-mountains;e2a6;253;186;116;50;30;10"
  "Rubble;nf-fa-cubes;f1b3;156;163;175;40;40;40"
  "Rubble;nf-fa-cube;f1b2;156;163;175;40;40;40"
  "Rubble;nf-md-cube_outline;f01a7;156;163;175;40;40;40"
  "Building;nf-fa-building;f1ad;165;180;252;20;20;50"
  "Building;nf-md-office_building;f0991;165;180;252;20;20;50"
  "Pavement;nf-fa-road;f018;203;213;225;50;50;50"
  "Pavement;nf-md-road;f0461;203;213;225;50;50;50"
  "Pavement;nf-md-road_variant;f0462;203;213;225;50;50;50"
  "Fire;nf-fa-fire;f06d;239;68;68;60;10;10"
  "Fire;nf-md-fire;f0238;239;68;68;60;10;10"
  "Tree Fire;nf-md-pine_tree_fire;f141a;239;68;68;60;10;10"
  "Direction N;nf-md-arrow_up;f005d;203;213;225;50;50;50"
  "Direction NE;nf-md-arrow_top_right;f005c;203;213;225;50;50;50"
  "Direction E;nf-md-arrow_right;f0054;203;213;225;50;50;50"
  "Direction SE;nf-md-arrow_bottom_right;f0043;203;213;225;50;50;50"
  "Direction S;nf-md-arrow_down;f0045;203;213;225;50;50;50"
  "Direction SW;nf-md-arrow_bottom_left;f0042;203;213;225;50;50;50"
  "Direction W;nf-md-arrow_left;f004d;203;213;225;50;50;50"
  "Direction NW;nf-md-arrow_top_left;f005b;203;213;225;50;50;50"
  "Direction N;nf-md-arrow_up_bold_outline;f09c7;203;213;225;50;50;50"
  "Direction NE;nf-md-arrow_top_right_bold_outline;f09c5;203;213;225;50;50;50"
  "Direction E;nf-md-arrow_right_bold_outline;f09c2;203;213;225;50;50;50"
  "Direction SE;nf-md-arrow_bottom_right_bold_outline;f09b9;203;213;225;50;50;50"
  "Direction S;nf-md-arrow_down_bold_outline;f09bf;203;213;225;50;50;50"
  "Direction SW;nf-md-arrow_bottom_left_bold_outline;f09b7;203;213;225;50;50;50"
  "Direction W;nf-md-arrow_left_bold_outline;f09c0;203;213;225;50;50;50"
  "Direction NW;nf-md-arrow_top_left_bold_outline;f09c3;203;213;225;50;50;50"

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

package tech.kinori.eclipse.p2mvn.maven;

public enum Mode {
        LOCAL("l"),
        DEPLOY("d"),
        INVALID("");

        private final String param;

        Mode(String param) {
            this.param = param;
        }

        public static Mode fromParam(String param) {
            switch (param) {
                case "":
                case "l":
                case "L":
                    return LOCAL;
                case "d":
                case "D":
                    return DEPLOY;
                default:
                    return INVALID;
            }
        }
    }